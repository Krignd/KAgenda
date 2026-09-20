package com.kstudio.agenda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.AiAssistant
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.AppSettings
import com.kstudio.agenda.data.ScheduleRepository
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.export.ImageExporter
import com.kstudio.agenda.export.ScheduleImageRenderer
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.FuzzyTime
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.model.WeekSchedule
import com.kstudio.agenda.notif.StatusNotification
import com.kstudio.agenda.overlay.FloatingBallService
import com.kstudio.agenda.util.AppLog
import com.kstudio.agenda.widget.NextClassWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        /** 打开 App 自动刷新的陈旧阈值：6 小时（学期课表极少中途变化，避免每次打开都全量重放） */
        const val AUTO_SYNC_STALE_MS = 6 * 60 * 60 * 1000L

        /** 自动刷新的启动延迟：避开启动首帧与 WebView 首次创建的主线程开销叠加 */
        const val AUTO_SYNC_DELAY_MS = 4_000L
    }

    private val repo = ScheduleRepository.get(app)

    /** 供 MainActivity 绑定无界面 WebView 宿主 */
    val engine: WebScheduleEngine = repo.engine

    val semester: StateFlow<SemesterSchedule?> = repo.semester
    val syncState: StateFlow<SyncUi> = repo.sync

    /** 当前查看的教学周（哨兵 MIN_VALUE 表示未指定，自动选择当前教学周） */
    private val _selectedWeekNo = MutableStateFlow(Int.MIN_VALUE)
    val selectedWeekNo: StateFlow<Int> = _selectedWeekNo.asStateFlow()

    /** 当前查看的周课表（由学期数据合成，供日/周视图与导出复用） */
    val weekSchedule: StateFlow<WeekSchedule?> =
        combine(repo.semester, _selectedWeekNo) { sem, selected ->
            sem?.let { it.week(resolveWeek(it, selected)) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** 本地日程 / 计划（用户自建，与课表独立；isPlan 区分归属） */
    val agenda: StateFlow<List<AgendaEvent>> = AgendaStore.events

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 图片导出进行中：防止连点产生重复图片与并发大位图渲染（低内存设备可能 OOM） */
    private val exportBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 一次性「定位并闪烁」请求（通知 / 小组件点击进入时使用） */
    private val _focusRequest = MutableStateFlow<FocusRequest?>(null)
    val focusRequest: StateFlow<FocusRequest?> = _focusRequest.asStateFlow()
    private var focusSeq = 0

    /** 外部入口定位：选中日期并请求对应课程/日期的闪烁反馈 */
    fun requestFocus(date: LocalDate, title: String) {
        selectDate(date)
        focusSeq++
        _focusRequest.value = FocusRequest(date.toEpochDay(), title.trim(), focusSeq)
    }

    fun clearFocus() {
        _focusRequest.value = null
    }

    /**
     * 小组件点击定位：优先定位“此刻正在进行”的课程（小组件状态可能滞后一分钟以上，
     * 直接按小组件里的目标会偏到“下一节课”），没有正在进行的课程时再按传入目标定位。
     */
    fun focusFromWidget(fallbackDate: LocalDate, fallbackTitle: String) {
        val now = LocalDateTime.now()
        val live = semester.value?.let { sem ->
            val wn = sem.teachingWeekOf(now.toLocalDate())
            if (wn in 1..40) {
                sem.weeks[wn].orEmpty()
                    .filter { it.dayOfWeek == now.dayOfWeek.value && it.occursInWeek(wn) }
                    .firstOrNull { c ->
                        val t = now.toLocalTime()
                        !t.isBefore(PeriodTimes.startOf(c.startPeriod)) &&
                            t.isBefore(PeriodTimes.endOf(c.endPeriod))
                    }
            } else null
        }
        if (live != null) requestFocus(now.toLocalDate(), live.title)
        else requestFocus(fallbackDate, fallbackTitle)
    }

    private var autoSyncTriggered = false

    /** 当前语言文案 */
    private val t get() = AppText.current

    init {
        // 日程文件读取放到 IO 线程（不在主线程做文件 IO；写操作内部也会确保已加载）
        viewModelScope.launch(Dispatchers.IO) {
            AgendaStore.ensureLoaded(getApplication())
        }
        viewModelScope.launch {
            // 应用已保存的语言（默认跟随系统）；同步系统级“按应用设置语言”
            val saved = SettingsStore.read(getApplication())
            AppText.state.value = AppLang.of(saved.appLanguage)
            AppText.applySystemLocale(getApplication(), AppText.state.value)

            settings.collect { s ->
                // 打开 App 自动刷新：有账号且（无缓存 或 超过 6 小时）时静默同步一次。
                // 阈值放宽 + 延后启动：减少不必要的全量重放，并避开首帧渲染与 WebView 首次创建的主线程开销叠加
                if (!autoSyncTriggered && s.autoRefresh && s.hasPassword) {
                    autoSyncTriggered = true
                    val stale = System.currentTimeMillis() - s.lastSyncAtMillis > AUTO_SYNC_STALE_MS
                    if (semester.value == null || stale) {
                        launch {
                            delay(AUTO_SYNC_DELAY_MS)
                            repo.syncNow(silent = true)
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ 交互动作

    fun selectDate(date: LocalDate) {
        val d = clampNavDate(date)
        _selectedDate.value = d
        // 跟随日期同步教学周（允许切到未抓取的周：week() 会返回空课表，视图仍可浏览）
        val sem = semester.value ?: return
        val week = sem.teachingWeekOf(d)
        if (_selectedWeekNo.value != week) {
            _selectedWeekNo.value = week
        }
    }

    /** 日期导航边界：当前年份往前 4 年的 1 月 1 日 ~ 往后 4 年的 12 月 31 日 */
    val navMinDate: LocalDate get() = LocalDate.of(LocalDate.now().year - 4, 1, 1)
    val navMaxDate: LocalDate get() = LocalDate.of(LocalDate.now().year + 4, 12, 31)

    fun clampNavDate(date: LocalDate): LocalDate = when {
        date.isBefore(navMinDate) -> navMinDate
        date.isAfter(navMaxDate) -> navMaxDate
        else -> date
    }

    /** 保存（新增或更新）本地日程 / 计划 */
    fun saveAgendaEvent(event: AgendaEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            AgendaStore.upsert(getApplication(), event)
            runCatching { StatusNotification.refresh(getApplication()) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
        message(if (event.isPlan) t.planSaved else t.agendaSaved)
    }

    /** 删除本地日程 / 计划 */
    fun deleteAgendaEvent(id: String) {
        val wasPlan = agenda.value.firstOrNull { it.id == id }?.isPlan == true
        viewModelScope.launch(Dispatchers.IO) {
            AgendaStore.delete(getApplication(), id)
            runCatching { StatusNotification.refresh(getApplication()) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
        message(if (wasPlan) t.planDeleted else t.agendaDeleted)
    }

    /** 清除全部日程与计划（保留课表缓存与账号） */
    fun clearAgendaData() {
        viewModelScope.launch(Dispatchers.IO) {
            AgendaStore.clear(getApplication())
            runCatching { StatusNotification.refresh(getApplication()) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
        message(t.msgEventsCleared)
    }

    /** 日视图：前后平移一天（周日 → 周一 自动跨周，周课表随之切换） */
    fun stepDay(delta: Int) = selectDate(_selectedDate.value.plusDays(delta.toLong()))

    /** 周视图：前后平移一周（不再限制在已抓取周次内，仅受导航边界限制） */
    fun stepWeek(delta: Int) = selectDate(_selectedDate.value.plusDays(7L * delta))

    /** 默认周次：优先当前教学周，否则取已抓取的最早一周 */
    private fun resolveWeek(sem: SemesterSchedule, selected: Int): Int {
        if (selected != Int.MIN_VALUE) return selected
        val cur = sem.teachingWeekOf(LocalDate.now())
        return when {
            sem.weeks.containsKey(cur) -> cur
            sem.weeks.isNotEmpty() -> sem.weeks.keys.min()
            else -> cur
        }
    }

    fun syncNow() = repo.syncNow()

    // 【已隐藏保留】网页登录返回回调：入口隐藏后暂无入口触发，代码保留未删除（恢复入口即可用）
    fun onWebLoginFinished(loggedIn: Boolean) {
        message(
            if (loggedIn) "网页登录完成，开始同步…"
            else "未检测到登录完成，正在尝试同步…"
        )
        repo.syncNow()
    }

    fun saveAccountAndSync(studentId: String, password: String?) {
        val id = studentId.trim()
        if (id.isBlank()) {
            message(t.msgNeedStudentId)
            return
        }
        val newPassword = password?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val saved = SettingsStore.setAccount(getApplication(), id, newPassword)
            if (!saved) {
                // 加密失败（極少见）：如实提示，不假装保存成功
                message(t.msgSaveFailed)
                return@launch
            }
            message(t.msgAccountSaved)
            // 输入了密码 = 用户在“登录”：强制用新密码真实登录一次，
            // 密码错误时明确报错（而不是沿用旧会话显示“已同步”）
            repo.syncNow(verifyCredentials = newPassword != null)
        }
    }

    fun logout() {
        viewModelScope.launch {
            SettingsStore.clearAccount(getApplication())
            repo.logoutAndClearSession()
            message(t.msgLoggedOut)
        }
    }

    /** 仅删除本地保存的学号密码（网页会话与课表缓存保留） */
    fun clearSavedCredentials() {
        viewModelScope.launch {
            repo.clearSavedCredentials()
            message(t.msgCredDeleted)
        }
    }

    /** 恢复初始状态（清账号/会话/缓存/日程/计划/提醒/日志/语言等设置） */
    fun resetApp() {
        viewModelScope.launch {
            repo.resetAll()
            AgendaStore.clear(getApplication())
            // 悬浮球属于“开箱状态”：重置时一并关闭并停止前台服务，避免重置后悬浮球仍残留
            FloatingBallService.stop(getApplication())
            _selectedWeekNo.value = Int.MIN_VALUE
            _selectedDate.value = LocalDate.now()
            autoSyncTriggered = false
            // 语言恢复为跟随系统
            AppText.state.value = AppLang.SYSTEM
            AppText.applySystemLocale(getApplication(), AppLang.SYSTEM)
            // 立即刷新常驻通知与桌面小组件：避免重置后仍显示旧内容（最长可残留 6 小时）
            withContext(Dispatchers.IO) {
                runCatching { StatusNotification.refresh(getApplication()) }
                runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
            }
            message(t.msgResetDone)
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setReminderEnabled(getApplication(), enabled)
            repo.rescheduleReminders()
            message(if (enabled) t.msgReminderOn else t.msgReminderOff)
        }
    }

    fun setLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            SettingsStore.setLeadMinutes(getApplication(), minutes)
            repo.rescheduleReminders()
        }
    }

    /** 仅重新排程提醒（开发者工具按钮） */
    fun rescheduleReminders() {
        repo.rescheduleReminders()
        message(t.rescheduled)
    }

    fun setAutoRefresh(enabled: Boolean) {
        viewModelScope.launch { SettingsStore.setAutoRefresh(getApplication(), enabled) }
    }

    /** 课程表模式（日程表页顶部勾选框） */
    fun setTimetableMode(enabled: Boolean) {
        viewModelScope.launch { SettingsStore.setTimetableMode(getApplication(), enabled) }
    }

    /** 时间线模式（非课程表）显示范围：开始/结束时间（分钟，0~1439；结束 ≤ 开始表示跨到次日） */
    fun setTimelineStart(minutes: Int) {
        viewModelScope.launch { SettingsStore.setTimelineStart(getApplication(), minutes) }
    }

    fun setTimelineEnd(minutes: Int) {
        viewModelScope.launch { SettingsStore.setTimelineEnd(getApplication(), minutes) }
    }

    /** 批量保存日程 / 计划（AI 快速添加等场景） */
    fun saveAgendaEvents(events: List<AgendaEvent>) {
        if (events.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 批量写盘：一次排序 + 一次落盘（原来逐条 upsert 会重复重写整文件）
            AgendaStore.upsertAll(getApplication(), events)
            runCatching { StatusNotification.refresh(getApplication()) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
        message(t.qaAdded(events.size))
    }

    /** AI 助手：执行“新增/修改/删除”操作并汇总反馈 */
    fun applyAiOps(ops: List<AiSkills.AiOp>) {
        if (ops.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = AiAssistant.apply(getApplication(), ops)
            runCatching { StatusNotification.refresh(getApplication()) }
            runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
            message(t.qaOpsDone(r.added, r.updated, r.deleted, r.unmatched))
        }
    }

    /** 新增自定义学校（适配代码 JSON）；成功后自动切换过去 */
    fun addCustomSchool(code: String, nameFallback: String, siteFallback: String) {
        viewModelScope.launch {
            // 适配器 JSON 解析与文件写入放到 IO 线程（不在主线程做文件 IO）
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    com.kstudio.agenda.model.Schools.addOrUpdate(
                        getApplication(), code, nameFallback, siteFallback,
                    )
                }
            }
            result.getOrNull()?.let { school ->
                SettingsStore.setSchool(getApplication(), school.id)
                message(t.adapterAdded(school.name))
            } ?: message(t.adapterInvalid(result.exceptionOrNull()?.message ?: "JSON"))
        }
    }

    /** 切换学校（见 Schools.ALL）；未适配学校仅切换展示，同步时会给出提示 */
    fun setSchool(id: String) {
        viewModelScope.launch {
            SettingsStore.setSchool(getApplication(), id)
            message(t.msgSchoolSwitched(Schools.of(id).name))
        }
    }

    /** 保存 AI Key（null=清除；加密存储；加密失败时如实提示，不假装成功） */
    fun setAiKey(key: String?) {
        viewModelScope.launch {
            val saved = SettingsStore.setAiKey(getApplication(), key)
            message(
                when {
                    !saved -> t.msgSaveFailed
                    key == null -> t.aiClearedToast
                    else -> t.aiSavedToast
                }
            )
        }
    }

    /** 切换「使用开发者的 API key」（默认勾选，使用内置 Key） */
    fun setUseDevAiKey(enabled: Boolean) {
        viewModelScope.launch { SettingsStore.setUseDevAiKey(getApplication(), enabled) }
    }

    fun setAiModel(model: String) {
        viewModelScope.launch { SettingsStore.setAiModel(getApplication(), model) }
    }

    /** 常驻通知：开关 + 内容源（course/plan/agenda 逗号分隔） */
    fun setStatusNotifConfig(enabled: Boolean, sourcesCsv: String) {
        viewModelScope.launch {
            SettingsStore.setStatusNotif(getApplication(), enabled, sourcesCsv)
            // 刷新常驻通知会读缓存，放到 IO 线程执行
            withContext(Dispatchers.IO) { StatusNotification.refresh(getApplication()) }
        }
    }

    /** 系统悬浮球开关：写入设置并启/停前台服务（无悬浮窗权限时给出提示） */
    fun setFloatingBall(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setFloatingBall(getApplication(), enabled)
            val ctx = getApplication<Application>()
            if (enabled) {
                if (android.provider.Settings.canDrawOverlays(ctx)) {
                    FloatingBallService.start(ctx)
                } else {
                    message(t.overlayNeedPerm)
                }
            } else {
                FloatingBallService.stop(ctx)
            }
        }
    }

    /** 切换应用语言：""=跟随系统；"zh"/"fr"/"en" */
    fun setAppLanguage(tag: String) {
        viewModelScope.launch {
            SettingsStore.setAppLanguage(getApplication(), tag)
            val lang = AppLang.of(tag)
            AppText.state.value = lang
            AppText.applySystemLocale(getApplication(), lang)
        }
    }

    fun clearCache() {
        repo.clearCache()
        message(t.msgCacheCleared)
    }

    /** 清除缓存与日志（重置区“清除缓存与日志”用） */
    fun clearCacheAndLogs() {
        repo.clearCache()
        AppLog.clear()
        message(t.msgCacheLogsCleared)
    }

    fun message(text: String) {
        viewModelScope.launch { _messages.emit(text) }
    }

    // ------------------------------------------------------------ 图片导出

    /** 日程表导出只包含“日程”（不含“计划”页的个人计划） */
    fun saveDayImage(date: LocalDate) {
        val week = weekSchedule.value
        if (week == null) {
            message(t.msgNoScheduleData)
            return
        }
        // 导出包含当天日程（含跨天长日程，不含计划），按时间排序
        val events = agenda.value
            .filter { !it.isPlan && it.coversDate(date) }
            .sortedWith(compareBy({ FuzzyTime.sortKey(it.startTime) }, { it.dateEpochDay }))
        if (!exportBusy.compareAndSet(false, true)) {
            message(t.msgExporting)
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    val bitmap = ScheduleImageRenderer.renderDay(week, date, events)
                    withContext(Dispatchers.IO) {
                        ImageExporter.saveToGallery(context, bitmap, "日课表_$date.png")
                    }
                }
                message(if (uri != null) AppText.current.msgSavedDayImage else t.msgSaveFailed)
            } finally {
                exportBusy.set(false)
            }
        }
    }

    fun saveWeekImage() {
        val week = weekSchedule.value
        if (week == null) {
            message(t.msgNoScheduleData)
            return
        }
        // 导出包含本周日程（按日期+时间排序，不含计划）
        val monday = week.monday
        val events = agenda.value
            .filter { ev -> !ev.isPlan && (0L..6L).any { off -> ev.coversDate(monday.plusDays(off)) } }
            .sortedWith(compareBy({ it.dateEpochDay }, { FuzzyTime.sortKey(it.startTime) }))
        if (!exportBusy.compareAndSet(false, true)) {
            message(t.msgExporting)
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    val bitmap = ScheduleImageRenderer.renderWeek(week, events)
                    withContext(Dispatchers.IO) {
                        ImageExporter.saveToGallery(context, bitmap, "周课表_第${week.weekNo}周.png")
                    }
                }
                message(if (uri != null) AppText.current.msgSavedWeekImage else t.msgSaveFailed)
            } finally {
                exportBusy.set(false)
            }
        }
    }

    /** 月课表导出（包含当月课程与日程；日程完整显示且不含计划） */
    fun saveMonthImage(monthStart: LocalDate) {
        val monthEnd = monthStart.plusDays(monthStart.lengthOfMonth().toLong() - 1)
        val events = agenda.value.filter { ev ->
            !ev.isPlan && !(ev.endDate.isBefore(monthStart) || ev.date.isAfter(monthEnd))
        }
        if (!exportBusy.compareAndSet(false, true)) {
            message(t.msgExporting)
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    val bitmap = ScheduleImageRenderer.renderMonth(
                        monthStart = monthStart,
                        semester = semester.value,
                        events = events,
                    )
                    withContext(Dispatchers.IO) {
                        ImageExporter.saveToGallery(
                            context,
                            bitmap,
                            "月课表_${monthStart.year}-%02d.png".format(monthStart.monthValue),
                        )
                    }
                }
                message(if (uri != null) AppText.current.msgSavedMonthImage else t.msgSaveFailed)
            } finally {
                exportBusy.set(false)
            }
        }
    }
}

/**
 * 一次性「定位并闪烁」请求（通知 / 小组件点击进入 App 时使用）：
 * @param epochDay 定位到的日期
 * @param title 需要闪烁的具体课程/日程标题（可能为空：只闪烁日期）
 * @param seq 递增序号（同一目标重复触发也会重新闪烁）
 */
data class FocusRequest(
    val epochDay: Long,
    val title: String,
    val seq: Int,
)
