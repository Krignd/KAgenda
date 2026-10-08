package com.kstudio.agenda.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.AiAssistant
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.AppSettings
import com.kstudio.agenda.data.BackupManager
import com.kstudio.agenda.data.CourseEditConflict
import com.kstudio.agenda.data.CourseEditStore
import com.kstudio.agenda.data.ExtraCoursesStore
import com.kstudio.agenda.data.LocalSmartParser
import com.kstudio.agenda.data.ScheduleRepository
import com.kstudio.agenda.data.SchoolFlows
import com.kstudio.agenda.data.SchoolToggleSpec
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.export.ImageExporter
import com.kstudio.agenda.export.ScheduleImageRenderer
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.FuzzyTime
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.model.WeekSchedule
import com.kstudio.agenda.notif.Notifier
import com.kstudio.agenda.notif.StatusNotification
import com.kstudio.agenda.overlay.FloatingBallService
import com.kstudio.agenda.util.AppLog
import com.kstudio.agenda.widget.NextClassWidgetUpdater
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.map
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

    /** 用户导入/手动添加的课程（与教务课表合并展示） */
    val importedCourses: StateFlow<List<Course>> = ExtraCoursesStore.courses

    /** 课程修改：已修改的课程数 + 同步后待用户确认的冲突 */
    val courseEditCount: StateFlow<Int> =
        combine(CourseEditStore.edits, repo.settings) { edits, s ->
            // 只统计当前学校的修正记录（切换学校后旧学校的记录仍保留，但不计入）
            edits.count { it.schoolId.isBlank() || it.schoolId == s.schoolId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val editConflicts: StateFlow<List<CourseEditConflict>> = repo.editConflicts

    /** 导入课程的「第 1 教学周周一」 */
    val importedAnchor: StateFlow<LocalDate?> =
        ExtraCoursesStore.anchorEpochDay
            .map { epoch -> epoch?.let { LocalDate.ofEpochDay(it) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        // 闪烁会话起点：界面上所有闪烁元素共用同一相位，总时长固定（切换视图不会“续期”）
        com.kstudio.agenda.ui.components.FlashSession.start()
        _focusRequest.value = FocusRequest(date.toEpochDay(), title.trim(), focusSeq)
    }

    fun clearFocus() {
        com.kstudio.agenda.ui.components.FlashSession.clear()
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
            // 法定节假日停课：不把当天的课当作「正在进行」（用户开启「节假日显示课表」时照常判定）
            if (wn in 1..40 && !HolidayTable.hidesCourses(now.toLocalDate())) {
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

    /** 手动登录时若正好有同步在跑，标记为待办，待当前同步结束后立刻做一次校验登录 */
    private var pendingVerifySync = false

    /** 当前语言文案 */
    private val t get() = AppText.current

    init {
        // 日程/导入课程文件读取放到 IO 线程（不在主线程做文件 IO；写操作内部也会确保已加载）
        viewModelScope.launch(Dispatchers.IO) {
            AgendaStore.ensureLoaded(getApplication())
            ExtraCoursesStore.ensureLoaded(getApplication())
            CourseEditStore.ensureLoaded(getApplication())
        }
        viewModelScope.launch {
            // 登录/会话类错误必须能被看到：除顶栏与横幅外，再用 Snackbar 提示一次
            // （避免用户以为“已同步”而实际未登录）
            repo.sync.collect { s ->
                if (s is SyncUi.NeedLogin && s.message.isNotBlank()) {
                    _messages.emit(s.message)
                }
                // 手动登录时若刚好有同步在进行，待其结束后自动补一次“校验新密码”的同步
                if (s !is SyncUi.Running && pendingVerifySync) {
                    pendingVerifySync = false
                    repo.syncNow(verifyCredentials = true)
                }
            }
        }
        viewModelScope.launch {
            // 同步后发现“教务数据既不是改前也不是改后”的课程修改冲突：用 Snackbar 提醒一次
            // （具体选择在弹窗里做：见 editConflicts）
            var lastConflicts = 0
            repo.editConflicts.collect { list ->
                if (list.size > lastConflicts) {
                    _messages.emit(t.msgEditConflicts(list.size))
                }
                lastConflicts = list.size
            }
        }
        viewModelScope.launch {
            // 应用已保存的语言（默认跟随系统）；同步系统级“按应用设置语言”
            val saved = SettingsStore.read(getApplication())
            AppText.state.value = AppLang.of(saved.appLanguage)
            AppText.applySystemLocale(getApplication(), AppText.state.value)

            settings.collect { s ->
                // 课程时间：用户自定义过就用自定义；否则套用当前学校的作息预设
                // （无预设的学校回落内置默认）。学校切换时这里会跟着切过去。
                val custom = PeriodTimes.decode(s.periodTimesRaw)
                if (custom != null) {
                    PeriodTimes.applyCustom(custom)
                } else {
                    val school = Schools.of(s.schoolId)
                    PeriodTimes.applySchoolPreset(SchoolFlows.of(school).periodPreset(school))
                }
                // 法定节假日是否照常显示课表（默认停课）
                HolidayTable.setShowCoursesOnHoliday(s.showHolidayCourses)
                // 当前学校：课表缓存归属 + 课程修正记录归属都依赖它
                Schools.setCurrent(s.schoolId)
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

    /** 回到今天（日视图「回到当前日」/ 周视图「回到当前周」共用；保持当前查看的视图） */
    fun goToday() = selectDate(LocalDate.now())

    /**
     * 依据已保存的学期锚点推算教学周（无锚点返回 null）。
     * 课表尚未加载（未登录 / 同步失败 / 已清缓存）时，界面上仍能显示「第 x 周」。
     */
    fun teachingWeekOf(date: LocalDate): Int? {
        val anchor = settings.value.anchorEpochDay ?: return null
        val week = Math.floorDiv(date.toEpochDay() - anchor, 7L).toInt() + 1
        return week.takeIf { it in 1..40 }
    }

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
            AppLog.i(
                "登录",
                "手动登录：学号=$id，" + if (newPassword != null) {
                    "已输入密码（先清会话再真实登录校验）"
                } else {
                    "未输入新密码（沿用已保存的密码，不校验）"
                }
            )
            // 用户未输入新密码时把话说清楚：否则容易误以为“刚填的密码被校验过了”
            message(if (newPassword != null) t.msgAccountSaved else t.msgAccountSavedKeepPwd)
            // 输入了密码 = 用户在“登录”：强制用新密码真实登录一次，
            // 密码错误时明确报错（而不是沿用旧会话显示“已同步”）
            if (newPassword != null && syncState.value is SyncUi.Running) {
                // 正在同步时不能并发：挂起待办，当前同步结束后自动执行校验登录
                pendingVerifySync = true
                AppLog.i("登录", "当前有同步在进行，已挂起“校验新密码”的同步")
            } else {
                repo.syncNow(verifyCredentials = newPassword != null)
            }
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
            // 先等仓储层的重置完成：它的第一步是清空日志，只有等它返回后再写收尾日志，
            // 这些行才不会被清空动作抹掉（审计信息都在这一段里）。
            repo.resetAllNow()

            val events = agenda.value.size
            // 文件 IO 放 IO 线程（本协程跑在主线程，避免重置时卡顿/ANR 风险）
            withContext(Dispatchers.IO) { runCatching { AgendaStore.clear(getApplication()) } }
            AppLog.i("设置", "已清空本地日程与计划：$events 条")

            // 悬浮球属于“开箱状态”：重置时一并关闭并停止前台服务，避免重置后悬浮球仍残留
            FloatingBallService.stop(getApplication())
            AppLog.i("设置", "悬浮球服务已停止")

            _selectedWeekNo.value = Int.MIN_VALUE
            _selectedDate.value = LocalDate.now()
            autoSyncTriggered = false
            // 语言恢复为跟随系统
            AppText.state.value = AppLang.SYSTEM
            AppText.applySystemLocale(getApplication(), AppLang.SYSTEM)
            AppLog.i("设置", "界面语言已恢复为跟随系统")

            // 立即刷新常驻通知与桌面小组件：避免重置后仍显示旧内容（最长可残留 6 小时）
            withContext(Dispatchers.IO) {
                runCatching { StatusNotification.refresh(getApplication()) }
                runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
            }
            AppLog.i("设置", "应用已重置为初始状态（等价于刚安装）")
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

    /** 自动检查更新（默认开）：关掉后启动与进入「关于&反馈」时都不再静默检查 */
    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch { SettingsStore.setAutoCheckUpdate(getApplication(), enabled) }
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
    /** AI 助手识别的运行状态（跑在 viewModelScope 里，离开界面也会继续） */
    data class AiRunState(
        val busy: Boolean = false,
        val msg: String = "",
        /** 识别出的可执行操作（预览里改动过的话以这里为准） */
        val ops: List<AiSkills.AiOp> = emptyList(),
        /** 本次识别用的原文（重新打开界面时回填输入框） */
        val src: String = "",
        /** 有结果但用户还没查看（顶栏显示「!」角标） */
        val unread: Boolean = false,
    )

    private val _aiRun = MutableStateFlow(AiRunState())
    val aiRun: StateFlow<AiRunState> = _aiRun.asStateFlow()

    /**
     * 后台识别（本地优先 + DeepSeek 兜底）。
     *
     * 与旧行为的关键区别：识别跑在 [viewModelScope] 而不是界面作用域，所以用户可以离开 AI 界面，
     * 识别完成后：1) 发一条 IMPORTANCE_HIGH 浮动通知（Heads-up 横幅）；
     * 2) 顶栏 DeepSeek 图标左侧亮起「!」角标；3) 点进去直接看到这次识别的结果。
     */
    fun recognizeAi(text: String, offlineOnly: Boolean = false) {
        val asked = text.trim()
        if (asked.isEmpty() || _aiRun.value.busy) return
        val t = AppText.current
        _aiRun.value = AiRunState(busy = true, msg = t.aiRunning, src = text)
        viewModelScope.launch {
            var result: List<AiSkills.AiOp> = emptyList()
            var note = ""
            var failed = false
            try {
                val local = runCatching { LocalSmartParser.parseEvents(asked, LocalDate.now()) }
                    .getOrDefault(emptyList())
                if (offlineOnly) {
                    if (local.isEmpty()) {
                        note = t.qaNothing
                    } else {
                        result = local
                        note = t.aiOfflineDone(local.size)
                    }
                } else {
                    val key = SettingsStore.effectiveAiKey(getApplication())
                    if (key.isNullOrBlank()) {
                        // 没有可用 Key：直接用本地识别结果
                        if (local.isEmpty()) {
                            note = t.aiNeedKey
                        } else {
                            result = local
                            note = t.aiOfflineDone(local.size)
                        }
                    } else {
                        val reply = AiClient.chat(
                            apiKey = key,
                            model = SettingsStore.effectiveAiModel(getApplication()),
                            systemPrompt = AiSkills.assistantSystemPrompt,
                            userPrompt = AiSkills.assistantUserPrompt(
                                asked,
                                LocalDate.now(),
                                AiAssistant.contextLines(agenda.value),
                                settings.value.profileText,
                            ),
                        )
                        val list = AiSkills.parseOpsReply(reply)
                        when {
                            list.isNotEmpty() -> result = list
                            local.isNotEmpty() -> {
                                result = local
                                note = t.aiOfflineDone(local.size)
                            }
                            else -> note = t.qaNothing
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed = true
                note = (e.message ?: t.parseFail).take(60)
            }
            _aiRun.value = AiRunState(
                busy = false,
                msg = note,
                ops = result,
                src = text,
                unread = result.isNotEmpty(),
            )
            if (result.isNotEmpty()) {
                runCatching { Notifier.showAiDone(getApplication(), result.size) }
            } else if (failed) {
                runCatching { Notifier.showAiFailed(getApplication(), note) }
            }
        }
    }

    /** 预览里逐条删除/修改后写回（再次打开界面时保持一致） */
    fun updateAiRunOps(ops: List<AiSkills.AiOp>) {
        _aiRun.value = _aiRun.value.copy(ops = ops, unread = ops.isNotEmpty())
    }

    /** 用户已看到结果：熄灭顶栏「!」角标（结果本身保留，直到执行或清空） */
    fun markAiResultSeen() {
        _aiRun.value = _aiRun.value.copy(unread = false)
    }

    /** 执行完/放弃后清空本次识别结果 */
    fun clearAiRun() {
        _aiRun.value = AiRunState()
    }

    /** 身份预设（学院/专业/年级/班级）：供 AI 在条件分支时按本人身份选择 */
    fun setProfile(college: String, major: String, grade: String, clazz: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SettingsStore.setProfile(getApplication(), college, major, grade, clazz)
        }
    }

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
            val previous = settings.value.schoolId
            SettingsStore.setSchool(getApplication(), id)
            Schools.setCurrent(id)
            if (previous != id) {
                // 切换学校：旧学校的课表缓存/学期锚点都不再适用
                // （缓存里有学校归属校验，不清理就只会读不到；锚点需主动清，否则会显示错误的教学周）
                SettingsStore.clearSemesterAnchor(getApplication())
                _selectedWeekNo.value = Int.MIN_VALUE
                if (settings.value.hasPassword && settings.value.autoRefresh) {
                    repo.syncNow(silent = true)
                }
            }
            message(t.msgSchoolSwitched(Schools.of(id).name))
        }
    }

    /**
     * 写回「学校专属开关」（由学校流程插件提供描述，如江苏大学的「通过 WebVPN」）。
     *
     * 界面只负责渲染与回调，具体含义与存储位置完全由插件决定，
     * 因此这里与设置界面都不含任何学校名字。
     */
    internal fun setSchoolToggle(spec: SchoolToggleSpec, enabled: Boolean) {
        viewModelScope.launch {
            spec.set(getApplication(), enabled)
            AppLog.i("设置", "学校开关 ${spec.id} = $enabled")
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

    /** 常驻通知里的「AI 快速添加」文本框入口开关（关闭后该条目不再常驻） */
    fun setStatusAiEntry(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setStatusAiEntry(getApplication(), enabled)
            withContext(Dispatchers.IO) { StatusNotification.refresh(getApplication()) }
        }
    }

    /** 小组件刷新频率：是否自定义 */
    fun setWidgetRefreshCustom(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setWidgetRefreshCustom(getApplication(), enabled)
            // 立即按新策略重排下一次刷新闹钟
            withContext(Dispatchers.IO) { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
    }

    /** 小组件刷新间隔（分钟）：tier 0=临近(≤1h) 1=较近(≤3h) 2=较远(>3h 或无日程) */
    fun setWidgetRefreshMinutes(tier: Int, minutes: Int) {
        viewModelScope.launch {
            SettingsStore.setWidgetRefreshMinutes(getApplication(), tier, minutes)
            withContext(Dispatchers.IO) { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
        }
    }

    /** 用户自定义课程时间（14 节起止）；传 null / 默认值时恢复默认作息 */
    fun setPeriodTimes(list: List<Pair<java.time.LocalTime, java.time.LocalTime>>?) {
        viewModelScope.launch {
            val raw = if (list == null) "" else PeriodTimes.encode(list)
            SettingsStore.setPeriodTimes(getApplication(), raw)
            PeriodTimes.applyCustom(PeriodTimes.decode(raw))
            // 课程时间变化会影响小组件与常驻通知中的时间文案，立即刷新
            withContext(Dispatchers.IO) {
                runCatching { StatusNotification.refresh(getApplication()) }
                runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
            }
            message(t.msgPeriodTimesSaved)
        }
    }

    // ------------------------------------------------------------ 课程导入

    /** 读取本地文件中的导入课程（应用启动时调用一次） */
    fun ensureImportedCoursesLoaded() {
        viewModelScope.launch(Dispatchers.IO) { ExtraCoursesStore.ensureLoaded(getApplication()) }
    }

    /** 导入课程表：与已有导入课程合并（去重），并立即刷新课表/小组件/通知 */
    fun importCourses(courses: List<Course>, anchor: LocalDate?) {
        if (courses.isEmpty()) {
            message(t.msgImportNoCourses)
            return
        }
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                ExtraCoursesStore.addAll(getApplication(), courses, anchor)
            }
            repo.onImportedCoursesChanged()
            message(t.msgImportedCourses(added))
        }
    }

    fun deleteImportedCourse(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ExtraCoursesStore.delete(getApplication(), id) }
            repo.onImportedCoursesChanged()
        }
    }

    fun clearImportedCourses() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ExtraCoursesStore.clear(getApplication()) }
            repo.onImportedCoursesChanged()
            message(t.msgImportedCoursesCleared)
        }
    }

    // ------------------------------------------------------------ 课程修改

    /**
     * 保存课程修改。
     * - 导入课程：直接改本地记录（不参与「与教务系统比对」）；
     * - 教务课程：写入修改记录（存档原课程），展示层立即生效并显示「已修改」标记。
     *
     * @param applyAll true=修改全部同一课程；false=仅修改这一次
     */
    fun saveCourseEdit(source: Course, edited: Course, applyAll: Boolean) {
        if (edited.title.isBlank()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            // 导入课程属于本地数据：直接改本地记录，不走「与教务系统比对」的流程
            val imported = importedCourses.value.any { it.id == source.id }
            withContext(Dispatchers.IO) {
                if (imported) {
                    if (applyAll) {
                        ExtraCoursesStore.replaceSeries(app, source, edited)
                    } else {
                        ExtraCoursesStore.replace(app, source.id, edited)
                    }
                } else {
                    CourseEditStore.save(app, source, edited, applyAll)
                }
            }
            // 导入课程走导入链路刷新；教务课程走课程修改链路（重排提醒 + 刷新小组件/常驻通知）
            if (imported) repo.onImportedCoursesChanged() else repo.onCourseEditsChanged()
            message(if (imported) t.msgImportedCourseEdited else t.msgCourseEditSaved)
        }
    }

    // ------------------------------------------------------------ 手动改课表：删除 / 新增 / 调休（调课）

    /** 某天「原本要上的课」（不受节假日停课影响）：调休/调课要能看到节假日当天的课 */
    fun coursesRawOnDate(date: LocalDate): List<Course> =
        semester.value?.rawCoursesOnDate(date).orEmpty().sortedBy { it.startPeriod }

    /** 删除课程：选中的周次里不再显示（选全部周次 = 整门课隐藏） */
    fun deleteCourse(source: Course, weeks: Set<Int>, applyAll: Boolean) {
        if (weeks.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val imported = importedCourses.value.any { it.id == source.id }
            withContext(Dispatchers.IO) {
                if (imported) {
                    val remain = source.weeksSet() - weeks
                    if (remain.isEmpty()) {
                        ExtraCoursesStore.delete(app, source.id)
                    } else {
                        ExtraCoursesStore.replace(
                            app,
                            source.id,
                            source.copy(weeksRaw = Course.encodeWeeks(remain)),
                        )
                    }
                } else {
                    CourseEditStore.saveDelete(app, source, weeks, applyAll)
                }
            }
            if (imported) repo.onImportedCoursesChanged() else repo.onCourseEditsChanged()
            message(t.msgCourseDeleted)
        }
    }

    /** 新增本地课程（教务系统里没有的课，只在本地显示） */
    fun addCourse(course: Course) {
        if (course.title.isBlank()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CourseEditStore.saveAdd(app, course) }
            repo.onCourseEditsChanged()
            message(t.msgCourseAdded)
        }
    }

    /**
     * 调休 / 调课：把 [sourceDate] 当天的 [courses] 迁到 [targetDate]。
     *
     * 导入课程直接改本地记录；教务课程走 [CourseEditStore.reschedule]，
     * 与「修改课程」完全同一套存档/比对/冲突逻辑。
     */
    fun moveCourses(sourceDate: LocalDate, courses: List<Course>, targetDate: LocalDate) {
        if (courses.isEmpty()) return
        if (sourceDate == targetDate) {
            message(t.msgMoveSameDay)
            return
        }
        val sem = semester.value
        val sourceWeek = sem?.teachingWeekOf(sourceDate) ?: teachingWeekOf(sourceDate)
        val targetWeek = sem?.teachingWeekOf(targetDate) ?: teachingWeekOf(targetDate)
        if (sourceWeek == null || targetWeek == null) {
            message(t.msgMoveNoWeek)
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val importedIds = importedCourses.value.map { it.id }.toSet()
            val academic = courses.filterNot { it.id in importedIds }
            val locals = courses.filter { it.id in importedIds }
            val moved = withContext(Dispatchers.IO) {
                var n = 0
                if (academic.isNotEmpty()) {
                    n += CourseEditStore.reschedule(app, sourceDate, sourceWeek, academic, targetDate, targetWeek)
                }
                if (locals.isNotEmpty()) {
                    for (c in locals) {
                        val remain = c.weeksSet() - sourceWeek
                        if (remain.isEmpty()) {
                            ExtraCoursesStore.delete(app, c.id)
                        } else {
                            ExtraCoursesStore.replace(
                                app,
                                c.id,
                                c.copy(weeksRaw = Course.encodeWeeks(remain)),
                            )
                        }
                    }
                    ExtraCoursesStore.addAll(
                        app,
                        locals.map {
                            it.copy(
                                dayOfWeek = targetDate.dayOfWeek.value,
                                weeksRaw = Course.encodeWeeks(setOf(targetWeek)),
                            )
                        },
                        null,
                    )
                    n += locals.size
                }
                n
            }
            repo.onCourseEditsChanged()
            repo.onImportedCoursesChanged()
            message(t.msgCourseMoved(moved, targetDate.monthValue, targetDate.dayOfMonth))
        }
    }

    /** 节假日是否照常显示课表（默认停课） */
    fun setShowHolidayCourses(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setShowHolidayCourses(getApplication(), enabled)
            HolidayTable.setShowCoursesOnHoliday(enabled)
            repo.rescheduleReminders()
            withContext(Dispatchers.IO) {
                runCatching { StatusNotification.refresh(getApplication()) }
                runCatching { NextClassWidgetUpdater.updateAndSchedule(getApplication()) }
            }
        }
    }

    // ------------------------------------------------------------ 数据备份 / 导入
    /**
     * 提交用户反馈。
     * 只发送：主题 + 用户写的内容 + 联系方式（可空）+ 应用版本 / 系统版本 / 当前学校；
     * 不发学号、密码、课表内容。
     */
    fun sendFeedback(topic: String, text: String, contact: String) {
        if (text.isBlank()) {
            message(t.feedbackEmpty)
            return
        }
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                com.kstudio.agenda.data.FeedbackClient.send(
                    com.kstudio.agenda.data.FeedbackClient.Payload(
                        topic = topic,
                        text = text,
                        contact = contact,
                        appVersion = com.kstudio.agenda.BuildConfig.VERSION_NAME,
                        school = settings.value.schoolId,
                        android = android.os.Build.VERSION.RELEASE ?: "",
                    )
                )
            }
            message(if (err == null) t.msgFeedbackSent else t.msgFeedbackFailed)
        }
    }

    /** 导出备份到默认位置（下载/KAgenda）；API 26–28 需要存储权限，按「用到才申请」处理 */
    fun exportBackupToDefault() {
        withExportPermission {
            viewModelScope.launch {
                val entry = withContext(Dispatchers.IO) {
                    val json = com.kstudio.agenda.data.BackupManager.buildJson(
                        getApplication(),
                        com.kstudio.agenda.BuildConfig.VERSION_NAME,
                        SettingsStore.exportPreferences(getApplication()),
                    )
                    com.kstudio.agenda.data.BackupStore.writeToDefault(getApplication(), json)
                }
                message(
                    if (entry != null) {
                        t.msgBackupExportedDefault(
                            com.kstudio.agenda.data.BackupStore.defaultLocationLabel(),
                            entry.name,
                        )
                    } else {
                        t.msgBackupExportFailed
                    }
                )
            }
        }
    }

    /** 默认位置（下载/KAgenda）已有的备份，按时间倒序；界面据此决定是直接选文件还是先问用户 */
    fun defaultBackups(): List<com.kstudio.agenda.data.BackupStore.Entry> =
        com.kstudio.agenda.data.BackupStore.listDefault(getApplication())

    /** 导出备份到用户选的文件 */
    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val size = BackupManager.exportTo(getApplication(), uri, com.kstudio.agenda.BuildConfig.VERSION_NAME)
            message(
                if (size > 0) t.msgBackupExported(com.kstudio.agenda.data.AppUpdater.formatBytes(size))
                else t.msgBackupExportFailed
            )
        }
    }

    /** 导入备份（覆盖本地数据文件；密码与 AI Key 保留） */
    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val r = BackupManager.importFrom(getApplication(), uri)) {
                is BackupManager.ImportResult.Success -> {
                    repo.reloadFromCache()
                    repo.onCourseEditsChanged()
                    repo.onImportedCoursesChanged()
                    message(t.msgBackupImported(r.agenda, r.edits, r.courses))
                }
                BackupManager.ImportResult.NotABackup -> message(t.msgBackupInvalid)
                is BackupManager.ImportResult.NewerVersion -> message(t.msgBackupNewer(r.version))
                is BackupManager.ImportResult.Failed -> message(t.msgBackupFailed(r.detail))
            }
        }
    }

    /** 仅还原课程修改（不联网）：把全部课程恢复为教务系统存档的原样 */
    fun restoreCourseEdits() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CourseEditStore.clear(getApplication()) }
            repo.dismissEditConflicts()
            repo.onCourseEditsChanged()
            message(t.msgCourseEditsRestored)
        }
    }

    /** 与教务系统同步并清除所有课程修改（清掉本地修改后立即重新同步） */
    fun syncAndClearCourseEdits() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CourseEditStore.clear(getApplication()) }
            repo.dismissEditConflicts()
            repo.onCourseEditsChanged()
            message(t.msgCourseEditsClearedSyncing)
            repo.syncNow()
        }
    }

    /** 冲突处理：跟随教务系统（放弃修改）/ 保留用户修改 */
    fun resolveEditConflict(conflict: CourseEditConflict, followAcademic: Boolean) {
        repo.resolveEditConflict(conflict, followAcademic)
        message(if (followAcademic) t.msgEditFollowed else t.msgEditKept)
    }

    /** 一次性处理全部冲突 */
    fun resolveAllEditConflicts(followAcademic: Boolean) {
        repo.resolveAllEditConflicts(followAcademic)
        message(if (followAcademic) t.msgEditFollowed else t.msgEditKept)
    }

    /** 稍后再处理：只关闭弹窗（修改继续保留，下次同步时再问） */
    fun dismissEditConflicts() = repo.dismissEditConflicts()

    /** 批量导入日程/计划（文档导入 / 离线识别共用） */
    fun importAgendaEvents(events: List<AgendaEvent>) {
        if (events.isEmpty()) {
            message(t.msgImportNoEvents)
            return
        }
        saveAgendaEvents(events)
    }

    /** 常驻通知锁屏显示开关 */
    fun setStatusOnLockScreen(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.setStatusOnLockScreen(getApplication(), enabled)
            withContext(Dispatchers.IO) { StatusNotification.refresh(getApplication()) }
        }
    }

    /** 界面风格（默认 / 液态玻璃） */
    fun setUiStyle(style: String) {
        viewModelScope.launch { SettingsStore.setUiStyle(getApplication(), style) }
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

    /**
     * Android 8.0–9 保存到公共相册需要存储权限：不在启动时申请，
     * 而是点「保存」的当下才申请（已授权 / Android 10+ 直接执行）。
     */
    private var pendingExport: (() -> Unit)? = null
    private val _exportNeedsPermission = MutableStateFlow(false)

    /** true 时 MainScreen 发起存储权限申请（Android 8.0–9 且尚未授权） */
    val exportNeedsPermission: StateFlow<Boolean> = _exportNeedsPermission.asStateFlow()

    private fun withExportPermission(action: () -> Unit) {
        val ctx = getApplication<Application>()
        val granted = Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingExport = action
            _exportNeedsPermission.value = true
        }
    }

    /** 存储权限申请结果：授权则继续本次导出，否则如实提示 */
    fun onExportPermissionResult(granted: Boolean) {
        val action = pendingExport
        pendingExport = null
        _exportNeedsPermission.value = false
        if (granted) action?.invoke() else message(t.msgStorageNeedPerm)
    }

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
        val context = getApplication<Application>()
        withExportPermission {
            if (!exportBusy.compareAndSet(false, true)) {
                message(t.msgExporting)
                return@withExportPermission
            }
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
        val context = getApplication<Application>()
        withExportPermission {
            if (!exportBusy.compareAndSet(false, true)) {
                message(t.msgExporting)
                return@withExportPermission
            }
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
    }

    /** 月课表导出（包含当月课程与日程；日程完整显示且不含计划） */
    fun saveMonthImage(monthStart: LocalDate) {
        val monthEnd = monthStart.plusDays(monthStart.lengthOfMonth().toLong() - 1)
        val events = agenda.value.filter { ev ->
            !ev.isPlan && !(ev.endDate.isBefore(monthStart) || ev.date.isAfter(monthEnd))
        }
        val context = getApplication<Application>()
        withExportPermission {
            if (!exportBusy.compareAndSet(false, true)) {
                message(t.msgExporting)
                return@withExportPermission
            }
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
