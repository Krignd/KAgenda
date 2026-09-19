package com.kstudio.agenda.data

import android.content.Context
import android.webkit.CookieManager
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.notif.ReminderScheduler
import com.kstudio.agenda.util.AppLog
import com.kstudio.agenda.widget.NextClassWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 同步状态（UI 依据它显示进度/错误/需要登录） */
sealed interface SyncUi {
    data object Idle : SyncUi
    data object Running : SyncUi
    data class NeedLogin(val message: String = "") : SyncUi
    data class Success(val atMillis: Long) : SyncUi
    data class Error(val message: String) : SyncUi
}

/**
 * 课表仓库：唯一的业务入口。
 * 负责：缓存加载、WebView 同步、设置读写、提醒调度。
 */
class ScheduleRepository private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "Repo"

        /** 单次同步的总超时（兜底，防止一直“正在同步”） */
        private const val SYNC_HARD_TIMEOUT_MS = 240_000L

        /** 增量同步的缓存新鲜期：7 天内的缓存 + 同一学期时，静默刷新只抓当前周 DOM，跳过整学期重放 */
        private const val INCREMENTAL_FRESH_MS = 7 * 24 * 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: ScheduleRepository? = null

        fun get(context: Context): ScheduleRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScheduleRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    val engine = WebScheduleEngine(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 在飞的同步任务（重置/登出时取消，避免继续跑完再覆盖状态） */
    private var syncJob: Job? = null

    /** 数据世代号：重置/登出/清缓存时 +1，使在飞同步的过期结果无法写回 */
    private val generationLock = Any()
    private var generation = 0L

    private fun bumpGeneration() = synchronized(generationLock) { generation++ }

    private fun currentGeneration(): Long = synchronized(generationLock) { generation }

    private val _semester = MutableStateFlow<SemesterSchedule?>(null)
    val semester: StateFlow<SemesterSchedule?> = _semester.asStateFlow()

    private val _sync = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    val settings: Flow<AppSettings> = SettingsStore.settingsFlow(appContext)

    /** 应用启动时调用：加载磁盘缓存并恢复提醒调度 */
    fun bootstrap() {
        scope.launch {
            ScheduleCache.load(appContext)?.let { _semester.value = it }
            runCatching { ReminderScheduler.reschedule(appContext) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(appContext) }
            runCatching { com.kstudio.agenda.notif.StatusNotification.refresh(appContext) }
        }
    }

    /**
     * 执行一次同步。
     * @param silent true 时不把失败暴露为错误状态（用于打开 App 时的后台自动刷新）
     */
    fun syncNow(credentials: Credentials? = null, silent: Boolean = false) {
        // 防误操作/并发：互斥判定在进入协程之前完成（原实现两次快速调用可能同时通过检查，
        // 导致两个同步流程并发运行、状态互相覆盖）
        synchronized(this) {
            if (_sync.value is SyncUi.Running) return
            _sync.value = SyncUi.Running
        }
        val generationAtStart = currentGeneration()
        // 静默自动刷新走增量模式：缓存较新且同学期时只抓当前周 DOM（由引擎内部判定并降级），
        // 显著减少联网耗时；手动刷新始终做完整同步
        val incrementalAgainst = if (silent) {
            val cached = ScheduleCache.load(appContext)
            if (cached != null && cached.semesterLabel.isNotBlank() &&
                System.currentTimeMillis() - cached.fetchedAtMillis < INCREMENTAL_FRESH_MS
            ) {
                cached.semesterLabel
            } else {
                null
            }
        } else {
            null
        }
        syncJob = scope.launch {
            val creds = credentials ?: SettingsStore.credentials(appContext)
            // 即使没有保存账号，也先让引擎用“已有网页会话”尝试抓取
            // （网页登录完成但未保存密码也可同步；未登录时由引擎引导登录）
            if (creds == null) {
                AppLog.i(TAG, "未保存账号，将先尝试使用已有网页会话")
            }
            AppLog.i(TAG, "开始同步（silent=$silent）")
            val result = withTimeoutOrNull(SYNC_HARD_TIMEOUT_MS) {
                engine.fetchWeek(creds, incrementalAgainstSemester = incrementalAgainst)
            } ?: SyncResult.Failure("同步超时（超过 4 分钟），请重试")
            // 重置/退出登录会提升世代号：过期的同步结果一律丢弃，
            // 防止“刚清空/登出，又被旧结果写回”的错乱状态
            if (generationAtStart != currentGeneration()) {
                AppLog.w(TAG, "同步结果已过期（同步期间执行了重置或退出登录），已忽略")
                return@launch
            }
            when (result) {
                is SyncResult.Success -> {
                    var semester = result.semester
                    // 防退化：若本次周数少于缓存（例如会话失效导致重放全部失败、只剩当前周 DOM），
                    // 与缓存合并（同周次以新数据为准），避免整个学期的课表被单周数据覆盖
                    runCatching {
                        val cached = ScheduleCache.load(appContext)
                        if (cached != null &&
                            cached.semesterLabel.isNotBlank() &&
                            cached.semesterLabel == semester.semesterLabel &&
                            cached.anchorEpochDay == semester.anchorEpochDay &&
                            semester.weeks.size < cached.weeks.size
                        ) {
                            val merged = LinkedHashMap(cached.weeks)
                            semester.weeks.forEach { (weekNo, courses) -> merged[weekNo] = courses }
                            AppLog.w(
                                TAG,
                                "本次仅抓到 ${semester.weeks.size} 周，与缓存合并为 ${merged.size} 周（防止课表缓存退化）"
                            )
                            semester = semester.copy(weeks = merged)
                        }
                    }
                    ScheduleCache.save(appContext, semester)
                    SettingsStore.setLastSync(appContext, semester.fetchedAtMillis)
                    SettingsStore.setSemesterAnchor(
                        appContext,
                        semester.semesterLabel.ifBlank { "当前学期" },
                        semester.anchorEpochDay,
                    )
                    _semester.value = semester
                    _sync.value = SyncUi.Success(semester.fetchedAtMillis)
                    runCatching { ReminderScheduler.reschedule(appContext) }
                    runCatching { NextClassWidgetUpdater.updateAndSchedule(appContext) }
                    runCatching { com.kstudio.agenda.notif.StatusNotification.refresh(appContext) }
                    AppLog.i(
                        TAG,
                        "同步成功：共 ${semester.weeks.size} 周，周次=${semester.weekNumbers}"
                    )
                }

                is SyncResult.LoginRequired -> {
                    AppLog.w(TAG, "需要登录：${result.message}")
                    _sync.value = SyncUi.NeedLogin(result.message)
                }

                is SyncResult.Failure -> {
                    AppLog.e(TAG, "同步失败：${result.message}", result.cause)
                    _sync.value = if (silent) SyncUi.Idle else SyncUi.Error(result.message)
                }
            }
        }
    }

    /** 仅重新调度提醒（不联网） */
    fun rescheduleReminders() {
        scope.launch { runCatching { ReminderScheduler.reschedule(appContext) } }
    }

    /** 仅删除本地保存的学号密码（保留网页会话与课表缓存，便于测试“无密码但有会话”场景） */
    fun clearSavedCredentials() {
        scope.launch {
            SettingsStore.clearAccount(appContext)
            AppLog.i(TAG, "已删除本地保存的学号密码")
        }
    }

    /** 退出登录：清除网页会话 Cookie（保留本地课表缓存便于离线查看） */
    fun logoutAndClearSession() {
        // 使在飞同步的写回失效并取消它：避免退出登录后又把“同步成功”状态写回来
        bumpGeneration()
        syncJob?.cancel()
        scope.launch {
            withContext(Dispatchers.Main) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }
            _sync.value = SyncUi.NeedLogin()
            AppLog.i(TAG, "已退出登录并清除网页会话 Cookie")
        }
    }

    /** 恢复初始状态：清账号/设置/缓存/已排闹钟/网页会话/日志 */
    fun resetAll() {
        // 取消在飞同步并使结果失效：防止重置后旧数据/旧状态被同步结果写回
        bumpGeneration()
        syncJob?.cancel()
        scope.launch {
            runCatching { ReminderScheduler.cancelAll(appContext) }
            SettingsStore.clearAll(appContext)
            ScheduleCache.clear(appContext)
            _semester.value = null
            _sync.value = SyncUi.Idle
            withContext(Dispatchers.Main) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }
            AppLog.clear()
            AppLog.i(TAG, "应用已重置为初始状态")
        }
    }

    fun clearCache() {
        // 清缓存同样使在飞同步失效：防止刚清完就被同步结果写回
        bumpGeneration()
        syncJob?.cancel()
        scope.launch {
            ScheduleCache.clear(appContext)
            _semester.value = null
        }
    }
}
