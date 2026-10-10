package com.kstudio.agenda.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.staticCompositionLocalOf
import com.kstudio.agenda.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * 应用内语言（中/法/英，默认跟随系统）。
 * - SYSTEM：跟随系统语言（中文→中文，法语→法语，其他→英文）；
 * - ZH / FR / EN：用户手动指定。
 */
enum class AppLang(val tag: String) {
    SYSTEM(""),
    ZH("zh"),
    FR("fr"),
    EN("en");

    companion object {
        fun of(tag: String?): AppLang = entries.firstOrNull { it.tag == (tag ?: "") } ?: SYSTEM
    }
}

/**
 * 应用文案集合（Kotlin 内实现，切换语言无需重建 Activity）。
 * 三个实现：ZhStrings / FrStrings / EnStrings。
 */
interface AppStrings {

    // ---------------- 通用 ----------------
    val appTitle: String
    val brand: String
    val save: String
    val cancel: String
    val delete: String
    val confirm: String
    val back: String
    val refresh: String
    val share: String

    // ---------------- 同步状态 ----------------
    val syncSyncedAt: String
    val syncNotLoggedIn: String
    val syncRunning: String
    val syncHintNeedLogin: String
    val syncHintRunning: String
    val noScheduleTitle: String
    val noScheduleHintDay: String
    val noScheduleHintWeek: String
    val noCoursesToday: String
    val noCoursesTodayHint: String
    val dayNoCoursesImage: String

    // ---------------- 底部导航 / 视图切换 ----------------
    val tabSchedule: String
    val tabPlan: String
    val tabOngoing: String
    val tabSettings: String
    val viewDay: String
    val viewWeek: String
    val viewMonth: String
    val timetableMode: String

    // ---------------- 导航 ----------------
    val prevWeek: String
    val nextWeek: String
    val prevMonth: String
    val nextMonth: String
    val backToThisMonth: String
    val today: String
    val saveDaySchedule: String
    val saveWeekSchedule: String
    val saveMonthSchedule: String

    fun weekNo(n: Int): String
    fun weekdayShort(i: Int): String          // 1=周一 … 7=周日（周一/Mon/Lun）
    fun weekdayNarrow(i: Int): String         // 一 / M / L（月视图表头）
    fun monthTitle(year: Int, month: Int): String
    fun dayImageTitle(month: Int, day: Int, dow: Int): String
    fun monthImageTitle(year: Int, month: Int): String
    val weekImageTitle: String

    // ---------------- 课程 ----------------
    fun periodNo(n: Int): String              // 第3节 / Period 3 / Période 3
    fun periodNoShort(n: Int): String         // 第3节 / #3 / #3
    fun periodsRange(a: Int, b: Int): String  // 第3-4节 / Periods 3-4 / Périodes 3-4
    fun courseCount(n: Int): String           // 共 X 节课
    fun eventCount(n: Int): String            // 日程 X 项
    fun weeksValue(raw: String): String       // 1-16周 / Weeks 1-16
    val detailTime: String
    val detailWeekday: String
    val detailTeacher: String
    val detailRoom: String
    val detailWeeks: String
    val detailCode: String
    val tagOngoing: String

    // ---------------- 日程 / 计划 ----------------
    val myAgenda: String
    val myAgendaWeek: String
    val addAgendaBtn: String
    val emptyAgendaHint: String
    val agendaSaved: String
    val agendaDeleted: String
    val myPlans: String
    val myPlansWeek: String
    val addPlanBtn: String
    val emptyPlansHint: String
    val planSaved: String
    val planDeleted: String
    val mixedTitle: String
    val tagLong: String
    val tagAgenda: String
    val tagPlan: String

    // ---------------- 编辑器 ----------------
    val editorAddAgenda: String
    val editorEditAgenda: String
    val editorAddPlan: String
    val editorEditPlan: String
    val fieldTitle: String
    val labelColor: String
    val colorAuto: String
    val segShort: String
    val segLong: String
    val segShortPlan: String
    val segLongPlan: String
    val secTimelineRange: String
    val secTimelineRangeSub: String
    val secWeekDays: String
    val secWeekDaysSub: String
    val weekDaysFive: String
    val weekDaysSeven: String
    val labelNextDay: String
    val secWidget: String
    val secWidgetSub: String
    // 小组件刷新频率（用户自定义）
    val secWidgetRefresh: String
    val secWidgetRefreshSub: String
    val widgetRefreshCustom: String
    val widgetRefreshCustomOn: String
    val widgetRefreshDefaultTag: String
    val widgetRefreshDefaultNote: String
    val widgetRefreshNear: String
    val widgetRefreshSoon: String
    val widgetRefreshFar: String
    val widgetPinHint: String
    val widgetPinUnsupported: String
    val fieldDate: String
    val fieldStartTime: String
    val fieldEndTime: String
    val fieldStartDate: String
    val fieldEndDate: String
    val unsetTime: String
    val fieldLocation: String
    val fieldNote: String
    val errTitle: String
    val errEndBeforeStart: String
    val askDeleteAgenda: String
    val askDeletePlan: String
    val pickerTimeTitle: String
    val repeatSection: String
    val repeatNone: String
    val repeatDailyMode: String
    val repeatWeeklyMode: String
    val repeatBiweeklyMode: String
    val repeatMonthlyMode: String
    val repeatFieldHint: String
    val errRepeatNoDay: String
    val repeatNeedDayHint: String
    fun repeatEveryNDays(n: Int): String
    fun repeatLabel(rule: String): String     // 规则 → 展示文案（空规则返回空串）
    fun deleteBody(name: String): String
    fun typeLabel(key: String): String        // 类型键 → 本地化名称（空键返回空串）

    // ---------------- 粘贴智能识别 ----------------
    val pasteLabel: String
    val pastePlaceholder: String
    val pasteBtn: String
    val parseOk: String
    val parsePartial: String
    val parseFail: String

    // ---------------- 进行中 ----------------
    fun ongoingHeader(n: Int): String
    val ongoingEmptyTitle: String
    val ongoingEmptyHint: String
    fun remaining(days: Long, hours: Long, mins: Long): String

    // ---------------- 设置 ----------------
    val secAccount: String
    /**
     * 「账号」卡片副标题。学校专属称呼统一放在 [SchoolTexts]（本接口的各语言实现里
     * 不写 `schoolId == "xxx"` 分支，新增学校只改 SchoolTexts 一处）。
     */
    fun secAccountSubOf(schoolId: String, schoolName: String): String
    val labelStudentId: String
    // ---------- 身份预设（AI 条件分支时按身份选择，见 SettingsStore profile*） ----------
    val secProfile: String
    val secProfileSub: String
    val labelCollege: String
    val labelMajor: String
    val labelGrade: String
    val labelClazz: String
    val btnSaveProfile: String
    // ---------- AI 后台识别完成后的浮动通知 / 顶栏角标 ----------
    val aiDoneTitle: String
    fun aiDoneBody(n: Int): String
    val aiFailedTitle: String
    val channelAiName: String
    val channelAiDesc: String
    val aiBadgeDesc: String
    val labelPassword: String
    val labelPasswordKeep: String
    val btnSaveAndSync: String
    val btnLogout: String
    val btnClearCred: String
    fun statusSaved(id: String): String
    val statusSession: String
    val statusNone: String
    fun lastSyncAt(time: String): String
    val neverSynced: String
    val accountFootnote: String
    /**
     * 江苏大学专用：是否「通过 WebVPN」访问教务系统。
     * 勾选与不勾选对应 data/UjsFlow.kt 里两份完全隔离的链路实现。
     */
    val labelViaWebVpn: String
    val labelViaWebVpnSub: String
    val askClearCredTitle: String
    val askClearCredBody: String

    val secReminder: String
    val secReminderSub: String
    val reminderEnable: String
    val remindAgendaLabel: String
    val leadLabel: String
    fun minutes(n: Int): String
    val exactOk: String
    val exactNo: String
    val gotoGrant: String
    val batteryOk: String
    val batteryNo: String
    val gotoExempt: String

    // 学校
    val secSchool: String
    val secSchoolSub: String
    val schoolChange: String
    fun msgSchoolSwitched(name: String): String

    // 学校适配器（添加学校）
    val schoolAdd: String
    val adapterName: String
    val adapterSite: String
    val adapterGen: String
    val adapterCodeLabel: String
    val adapterTemplate: String
    val adapterCopied: String
    val adapterSave: String
    val adapterNeedName: String
    fun adapterInvalid(msg: String): String
    fun adapterAdded(name: String): String
    val adapterExtraLabel: String
    val adapterExtraHint: String
    /** 「按 JSON 添加学校」功能可用性警告（入口保留，但如实提示可用性存疑） */
    val adapterWarnTitle: String
    val adapterWarnBody: String
    val adapterStageRequest: String
    val adapterStageParse: String
    val adapterStageDone: String
    fun adapterWaiting(sec: Int): String

    // AI 快速添加（悬浮入口）
    val qaTitle: String
    val qaHint: String
    val qaParse: String
    val qaConfirm: String
    val qaNothing: String
    val qaStaleResult: String
    val qaReparseTitle: String
    val qaReparseMsg: String
    val opDateInvalid: String
    val discardTitle: String
    val discardOk: String
    val msgExporting: String
    val qaPlanTag: String
    val qaAgendaTag: String
    fun qaAdded(n: Int): String

    // AI 助手操作（新增/修改/删除）
    val opAdd: String
    val opUpdate: String
    val opDelete: String
    val opMatchTitle: String
    val opMatchDate: String
    val opNewTitle: String
    val opNewDate: String
    val opNewStart: String
    val opNewEnd: String
    val opNewLocation: String
    fun qaOpsDone(added: Int, updated: Int, deleted: Int, unmatched: Int): String

    // 时间选择：具体 / 模糊
    val timeExact: String
    val timeFuzzy: String
    val timeFuzzyHint: String

    // 系统悬浮球（app 外可用）
    val secOverlay: String
    val secOverlaySub: String
    val overlayEnable: String
    val overlayHint: String
    val overlayNeedPerm: String
    val overlayGrant: String
    val qaOpenInApp: String
    val overlayNotifTitle: String
    val overlayNotifText: String
    val overlayNotifStop: String
    val qaReParse: String

    // AI 识别（DeepSeek）
    val secAi: String
    val secAiSub: String
    val aiKeyLabel: String
    val aiKeyKeepHint: String
    val aiUseDevKey: String
    val aiDevKeyInUse: String
    val aiDevModelFixed: String
    val aiModelLabel: String
    val aiSave: String
    val aiClear: String
    val aiSavedToast: String
    val aiClearedToast: String
    val aiBtn: String
    val aiRunning: String
    val aiOk: String
    val aiNeedKey: String

    // 常驻通知
    val secStatus: String
    val secStatusSub: String
    val statusEnable: String
    val statusSrcCourse: String
    val statusSrcPlan: String
    val statusSrcAgenda: String
    /** 常驻通知里的「AI 快速添加」文本框入口开关 */
    val statusSrcAi: String
    val statusHint: String
    val statusChannelName: String
    val statusChannelDesc: String
    fun statusInClassFmt(name: String, end: String): String
    fun statusNextClassFmt(name: String, time: String, left: String): String
    fun statusOngoingPlanFmt(name: String): String
    fun statusNextPlanFmt(name: String, time: String): String
    fun statusNextAgendaFmt(name: String, time: String): String
    val statusAiEntryTitle: String
    val statusAiEntryHint: String
    val notifOk: String
    val notifNo: String
    val enableNotif: String

    val secData: String
    val secDataSub: String
    val autoRefresh: String
    val autoCheckUpdate: String
    /** 重复日程的「截止日期」字段（与开始同日表示不限、持续延伸） */
    val fieldRepeatUntil: String
    /** 重复截止未填时的显示文案（不限、持续延伸） */
    val repeatUntilNone: String
    /** 底部栏中间的「学校」页签名 */
    val tabSchool: String
    /** 占位文案（页面尚未完成） */
    val comingSoon: String
    val btnSyncNow: String
    val btnClearCache: String
    val btnSaveDayImg: String
    val btnSaveWeekImg: String
    val btnSaveMonthImg: String
    // 设置页选项卡
    val settingsTabFeature: String
    val settingsTabCustom: String
    val settingsTabAccount: String
    // 分类菜单里的分类说明（设置首屏）
    val settingsTabAccountSub: String
    val settingsTabFeatureSub: String
    val settingsTabCustomSub: String
    val secLangSub: String
    // 权限设置卡片
    val secPermissions: String
    val secPermissionsSub: String
    val permGranted: String
    val permNotGranted: String
    val permRequest: String
    val permNetworkName: String
    val permNetworkWhy: String
    val permNotifName: String
    val permNotifWhy: String
    val permOverlayName: String
    val permOverlayWhy: String
    val permExactName: String
    val permExactWhy: String
    val permBatteryName: String
    val permBatteryWhy: String
    val permStorageName: String
    val permStorageWhy: String
    /** 安装未知应用（应用内更新安装新版 APK 需要；属特殊权限，需用户去系统设置开启） */
    val permInstallName: String
    val permInstallWhy: String
    fun permMissingCount(n: Int): String
    val imgDirNote: String

    val secLang: String
    val langSystem: String

    val secDev: String
    val secDevSub: String
    val openDevTools: String
    /**
     * 开发者工具：内置浏览器入口（打开应用内的网页窗口，用于模拟登录与抓取）。
     */
    val secDevBrowser: String
    val secDevBrowserSub: String
    val btnOpenDevBrowser: String
    /** 内置浏览器界面（普通浏览器：地址栏 / 前进后退 / 刷新 / 访问记录） */
    val devBrowserTitle: String
    val devBrowserAddress: String
    val devBrowserGo: String
    val devBrowserForward: String
    val devBrowserRefresh: String
    val devBrowserHistory: String
    val devBrowserClear: String
    val devBrowserEmpty: String
    val devBrowserClose: String
    val secNotifTest: String
    val secNotifTestSub: String
    val sendTestNotifBtn: String
    val sendTestReminderBtn: String
    val testNotifSent: String
    val testReminderScheduled: String
    val secReminderTools: String
    fun reminderCountLabel(n: Int): String
    val rescheduleBtn: String
    val cancelAlarmsBtn: String
    val rescheduled: String
    val alarmsCancelled: String

    val secReset: String
    val secResetSub: String
    val resetClearData: String
    val resetClearDataDesc: String
    val resetClearCache: String
    val resetClearCacheDesc: String
    /** 重置区里的「退出登录」说明（开发者工具里用它模拟“装好后未登录”的状态） */
    val resetLogoutDesc: String
    val resetAll: String
    val resetAllDesc: String
    val askResetTitle: String
    val askResetBody: String
    val askClearDataTitle: String
    val askClearDataBody: String
    val askClearCacheTitle: String
    val askClearCacheBody: String

    val secAbout: String
    val settingsTabAboutFeedback: String
    val aboutSub: String
    val aboutBody: String
    val aboutTip: String
    val aboutPeriods: String

    // ---------------- 应用内更新（检查 / 下载 / 安装） ----------------
    /** 「设置 → 关于」里的检查更新卡片标题 */
    val updateCheck: String
    fun updateCurrent(name: String): String
    val updateChecking: String
    val updateLatest: String
    fun updateAvailable(name: String, size: String): String
    val updateDownloadAndInstall: String
    val updateInstall: String
    fun updateDownloading(percent: Int): String
    fun updateDownloaded(size: String): String
    fun updateStartDownload(size: String): String
    fun updateFailed(msg: String): String
    /** 顶栏「K日程」右侧的小字提示 */
    val updateHintAvailable: String
    fun updateHintDownloading(percent: Int): String
    val updateHintInstall: String
    val updateSizeUnknown: String
    val updatePermissionTitle: String
    val updatePermissionBody: String
    val updateOpenSettings: String
    val updatePackageInvalid: String

    // ---------------- 日志 / 开发者工具 ----------------
    val logTitle: String
    val logDesc: String
    val viewShareLog: String
    val logFilterAll: String
    val logFilterWarn: String
    val logFilterError: String
    val clearLogBtn: String
    val readingLog: String
    val emptyLog: String
    val logShareSubject: String
    val shareChooseFormat: String
    val shareFormatHint: String
    val shareAsTxt: String
    val shareAsMd: String
    val logMdTime: String
    val logMdVersion: String
    val logMdDevice: String
    val diagTitle: String
    val diagVersion: String
    val diagSync: String
    val diagLastSync: String
    val diagNone: String

    // ---------------- 提示消息（ViewModel） ----------------
    val msgNeedStudentId: String
    val msgAccountSaved: String
    val msgAccountSavedKeepPwd: String
    val msgLoggedOut: String
    val msgCredDeleted: String
    val msgResetDone: String
    val msgEventsCleared: String
    val msgCacheLogsCleared: String
    val msgCacheCleared: String
    val msgReminderOn: String
    val msgReminderOff: String
    val msgNoScheduleData: String
    val msgSaveFailed: String
    val msgStorageNeedPerm: String
    val msgSavedDayImage: String
    val msgSavedWeekImage: String
    val msgSavedMonthImage: String

    // ---------------- 通知 / 导出 ----------------
    val channelName: String
    val channelDesc: String
    fun notifClassSoon(title: String): String
    fun notifAgendaSoon(title: String): String
    val syncTimeLabel: String
    val exportTimeLabel: String

    // ---------------- 回到今天 / 本周 ----------------
    val backToToday: String
    val backToThisWeek: String

    // ---------------- 自定义课程时间 ----------------
    val secPeriodTimes: String
    val secPeriodTimesSub: String
    val btnEditPeriodTimes: String
    val periodTimesTitle: String
    val periodTimesHint: String
    val periodTimesRestoreDefault: String
    val periodTimesRowFmt: String          // “第1节”
    fun periodTimesCount(n: Int): String
    fun periodTimesCountWarning(maxUsed: Int, configured: Int): String
    val periodTimesAddOne: String
    val periodTimesRemoveOne: String
    val msgPeriodTimesSaved: String
    val msgPeriodTimesInvalid: String
    val periodTimesDefaultTag: String
    val periodTimesCustomTag: String

    // ---------------- 锁屏显示 ----------------
    val secLockScreen: String
    val secLockScreenSub: String
    val btnOpenNotifSettings: String
    val lockScreenHint: String

    // ---------------- 界面风格（液态玻璃） ----------------
    val secUiStyle: String
    val secUiStyleSub: String
    val uiStyleDefault: String
    val uiStyleDefaultSub: String
    val uiStyleGlass: String
    val uiStyleGlassSub: String

    // ---------------- 文档导入 / 导出 ----------------
    val secDocs: String
    val secDocsSub: String
    val docTitle: String
    val docIntro: String
    val docOpenHint: String
    val docPick: String
    val docReading: String
    fun docReadFail(msg: String): String
    fun docUnsupportedExt(ext: String): String
    val docNoText: String
    val docTextLabel: String
    fun docDetected(events: Int, courses: Int): String
    val docImportAgenda: String
    val docImportPlan: String
    val docImportCourses: String
    fun docAnchorLabel(date: String): String
    val docAnchorHint: String
    fun docImportedList(n: Int): String
    val docClearImported: String
    val docExport: String
    val docExportHint: String
    val docExportCsv: String
    val docExportMd: String
    val docExported: String
    val docExportFailed: String
    val msgImportedCoursesCleared: String
    fun msgImportedCourses(n: Int): String
    val msgImportNoCourses: String
    val msgImportNoEvents: String
    val docLocalOnly: String

    // ---------------- AI 助手的本地识别 ----------------
    val aiOfflineParse: String
    fun aiOfflineDone(n: Int): String

    // ---------------- 课程修改（教务系统未更新时的本地修正） ----------------
    val tagEdited: String
    val btnEditCourse: String
    val courseEditTitle: String
    val courseEditIntro: String
    val editScopeLabel: String
    val editScopeOne: String
    val editScopeOneHint: String
    val editScopeAll: String
    val editScopeAllHint: String
    val fieldCourseName: String
    val editWeeksHint: String
    val editPeriodStart: String
    val editPeriodEnd: String
    val editNoChange: String
    val editSavedNote: String
    val msgCourseEditSaved: String
    val msgImportedCourseEdited: String
    val msgCourseEditsRestored: String
    val msgCourseEditsClearedSyncing: String
    val msgEditFollowed: String
    val msgEditKept: String
    fun msgEditConflicts(n: Int): String

    // 设置项：「课程修改」
    val secCourseEdits: String
    val secCourseEditsSub: String
    fun courseEditsCount(n: Int): String
    val courseEditsNone: String
    val courseEditsIntro: String
    val btnRestoreCourseEdits: String
    val btnSyncClearCourseEdits: String
    val courseEditsHint: String
    val askRestoreEditsTitle: String
    val askRestoreEditsBody: String
    val askSyncClearEditsTitle: String
    val askSyncClearEditsBody: String

    // 同步后的冲突询问
    val editConflictTitle: String
    val editConflictIntro: String
    val editConflictLatest: String
    val editConflictYours: String
    val editConflictMissing: String
    val btnFollowAcademic: String
    val btnKeepMine: String
    val btnFollowAll: String
    val btnKeepAll: String
    val editConflictLater: String

    // ---------------- 手动改课表：删除 / 新增 / 调课（调休） ----------------
    val editActionModify: String
    val editActionDelete: String
    val editActionMove: String
    val editDeleteIntro: String
    val editDeleteWeeksHint: String
    fun editMoveIntro(n: Int): String
    val editMoveWholeDay: String
    fun editMoveWholeDayHint(n: Int): String
    val editMoveSingle: String
    val editMoveSingleHint: String
    val editMoveTarget: String
    val editMovePickDate: String
    val weeksAll: String
    val weeksNone: String
    val courseAddTitle: String
    val courseAddIntro: String
    val btnAddCourse: String
    val btnCopyCourse: String
    val aboutOfficialSite: String
    val aboutGithubRepo: String

    // ---------------- 用户反馈 ----------------
    val secFeedback: String
    val secFeedbackSub: String
    val feedbackIntro: String
    val feedbackTopic: String
    val topicBug: String
    val topicFeature: String
    val topicSchool: String
    val topicOther: String
    val feedbackPlaceholder: String
    val feedbackContact: String
    val btnFeedbackSend: String
    val msgFeedbackSent: String
    val msgFeedbackFailed: String
    val feedbackEmpty: String
    val feedbackIncludeNote: String
    val btnRescheduleDate: String
    val longPressHint: String
    val rescheduleTitle: String
    fun rescheduleIntro(month: Int, day: Int, n: Int): String
    val rescheduleSingleHint: String
    val msgCourseDeleted: String
    val msgCourseAdded: String
    fun msgCourseMoved(n: Int, month: Int, day: Int): String
    val msgMoveSameDay: String
    val msgMoveNoWeek: String

    // ---------------- 节假日显示课表 ----------------
    val secHolidayCourses: String
    val secHolidayCoursesSub: String
    val holidayCoursesSwitch: String
    val holidayCoursesNote: String

    // ---------------- 数据备份与导入 ----------------
    val secBackup: String
    val secBackupSub: String
    val backupIntro: String
    val backupIncludes: String
    val btnBackupExport: String
    val btnBackupImport: String
    val backupAskImportTitle: String
    val backupAskImportBody: String
    val backupFileName: String
    val backupExportTitle: String
    val backupExportDefault: String
    val backupExportCustom: String
    fun msgBackupExportedDefault(dir: String, name: String): String
    val backupImportTitle: String
    fun backupImportFound(n: Int, dir: String): String
    val backupImportPickOther: String
    fun backupDefaultDirNote(dir: String): String
    fun msgBackupExported(size: String): String
    val msgBackupExportFailed: String
    fun msgBackupImported(agenda: Int, edits: Int, courses: Int): String
    val msgBackupInvalid: String
    fun msgBackupNewer(version: Int): String
    fun msgBackupFailed(detail: String): String
}

// ==================================================================== 中文

object ZhStrings : AppStrings {
    override val appTitle = "K日程"
    override val brand = "K日程"
    override val save = "保存"
    override val cancel = "取消"
    override val delete = "删除"
    override val confirm = "确定"
    override val back = "返回"
    override val refresh = "刷新"
    override val share = "分享"

    override val syncSyncedAt = "已同步 · "
    override val syncNotLoggedIn = "未登录"
    override val syncRunning = "正在同步…"
    override val syncHintNeedLogin = "尚未登录。请在「设置」页输入学号密码并同步。"
    override val syncHintRunning = "正在从教务系统获取课表…"
    override val noScheduleTitle = "暂无课表数据"
    override val noScheduleHintDay = "请先在「设置」页输入学号密码登录，然后点击右上角刷新按钮从教务系统获取课表。"
    override val noScheduleHintWeek = "请先在「设置」页登录并同步，之后即可查看整周课表。"
    override val noCoursesToday = "这一天没有课程"
    override val noCoursesTodayHint = "可以切换到其他日期查看，或点击右上角刷新同步最新数据。"
    override val dayNoCoursesImage = "这一天没有课程安排 🎉"

    override val tabSchedule = "日程表"
    override val tabPlan = "计划"
    override val tabOngoing = "进行中"
    override val tabSettings = "设置"
    override val viewDay = "日视图"
    override val viewWeek = "周视图"
    override val viewMonth = "月视图"
    override val timetableMode = "课程表模式"

    override val prevWeek = "上一周"
    override val nextWeek = "下一周"
    override val prevMonth = "上个月"
    override val nextMonth = "下个月"
    override val backToThisMonth = "回到本月"
    override val today = "今天"
    override val saveDaySchedule = "保存日课表"
    override val saveWeekSchedule = "保存周课表"
    override val saveMonthSchedule = "保存月课表"

    override fun weekNo(n: Int) = "第${n}周"
    override fun weekdayShort(i: Int) =
        arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[(i - 1).coerceIn(0, 6)]
    override fun weekdayNarrow(i: Int) =
        arrayOf("一", "二", "三", "四", "五", "六", "日")[(i - 1).coerceIn(0, 6)]
    override fun monthTitle(year: Int, month: Int) = "${year}年${month}月"
    override fun dayImageTitle(month: Int, day: Int, dow: Int) =
        "${month}月${day}日 ${weekdayShort(dow)}"

    override fun monthImageTitle(year: Int, month: Int) = "月课表 · ${year}年${month}月"
    override val weekImageTitle = "周课表"

    override fun periodNo(n: Int) = "第${n}节"
    override fun periodNoShort(n: Int) = "第${n}节"
    override fun periodsRange(a: Int, b: Int) = "第${a}-${b}节"
    override fun courseCount(n: Int) = "共${n}节课"
    override fun eventCount(n: Int) = "日程${n}项"
    override fun weeksValue(raw: String) = "${raw}周"
    override val detailTime = "时间"
    override val detailWeekday = "星期"
    override val detailTeacher = "教师"
    override val detailRoom = "教室"
    override val detailWeeks = "周次"
    override val detailCode = "课程号"
    override val tagOngoing = "进行中"

    override val myAgenda = "我的日程"
    override val myAgendaWeek = "我的日程（本周）"
    override val addAgendaBtn = "＋ 添加日程"
    override val emptyAgendaHint = "暂无日程，点右上「＋ 添加日程」新建"
    override val agendaSaved = "日程已保存"
    override val agendaDeleted = "日程已删除"
    override val myPlans = "我的计划"
    override val myPlansWeek = "我的计划（本周）"
    override val addPlanBtn = "＋ 添加计划"
    override val emptyPlansHint = "暂无计划，点右上「＋ 添加计划」新建"
    override val planSaved = "计划已保存"
    override val planDeleted = "计划已删除"
    override val mixedTitle = "今日安排（课程 + 日程）"
    override val tagLong = "长日程"
    override val tagAgenda = "日程"
    override val tagPlan = "计划"

    override val editorAddAgenda = "添加日程"
    override val editorEditAgenda = "编辑日程"
    override val editorAddPlan = "添加计划"
    override val editorEditPlan = "编辑计划"
    override val fieldTitle = "标题"
    override val labelColor = "颜色"
    override val colorAuto = "自动"
    override val segShort = "短日程"
    override val segLong = "长日程"
    override val segShortPlan = "短计划"
    override val segLongPlan = "长计划"
    override val secTimelineRange = "时间线显示范围"
    override val secTimelineRangeSub = "非课程表模式下周视图的起止时间；结束早于开始表示跨到次日（默认 06:00 – 次日 02:00）"
    override val secWeekDays = "周视图天数"
    override val secWeekDaysSub = "周视图默认显示的天数；「日程表」与「计划」共用（可双指缩放调整）"
    override val weekDaysFive = "5 天（周一~周五）"
    override val weekDaysSeven = "7 天（周一~周日）"
    override val labelNextDay = "次日"
    override val secWidget = "小组件"
    override val secWidgetSub = "一键把课表小组件添加到桌面"
    override val secWidgetRefresh = "小组件刷新频率"
    override val secWidgetRefreshSub = "越接近下个日程刷新越勤；默认 1 / 5 / 60 分钟"
    override val widgetRefreshCustom = "自定义刷新频率"
    override val widgetRefreshCustomOn = "自定义"
    override val widgetRefreshDefaultTag = "默认"
    override val widgetRefreshDefaultNote =
        "默认频率：距下个日程 ≤1 小时每分钟；≤3 小时每 5 分钟；更远或无日程每 60 分钟。打开上方开关即可自定义。"
    override val widgetRefreshNear = "距下个日程 ≤ 1 小时"
    override val widgetRefreshSoon = "≤ 3 小时"
    override val widgetRefreshFar = "更远 / 暂无日程"
    override val widgetPinHint = "点选尺寸即可添加到桌面；若桌面不支持应用内添加，可长按桌面空白处从小组件列表中选择「K日程」"
    override val widgetPinUnsupported = "当前桌面不支持应用内快捷添加：请长按桌面空白处，从小组件列表中添加 K日程"
    override val fieldDate = "日期"
    override val fieldStartTime = "开始时间"
    override val fieldEndTime = "结束时间"
    override val fieldStartDate = "开始日期"
    override val fieldEndDate = "结束日期"
    override val unsetTime = "未设置"
    override val fieldLocation = "地点（可选）"
    override val fieldNote = "备注（可选）"
    override val errTitle = "请输入标题"
    override val errEndBeforeStart = "结束时间不能早于开始时间"
    override val askDeleteAgenda = "删除日程？"
    override val askDeletePlan = "删除计划？"
    override val pickerTimeTitle = "选择时间"
    override val repeatSection = "重复"
    override val repeatNone = "不重复"
    override val repeatDailyMode = "按天"
    override val repeatWeeklyMode = "每周"
    override val repeatBiweeklyMode = "隔周"
    override val repeatMonthlyMode = "每月"
    override val repeatFieldHint = "例如：每周二/四/六、隔周周二、每3天（留空保持不变）"
    override val errRepeatNoDay = "未选择星期：请至少选择一个重复的星期后再保存"
    override val repeatNeedDayHint = "请至少选择一个星期（未选择无法保存）"
    override fun repeatEveryNDays(n: Int) = if (n <= 1) "每天" else "每${n}天"
    override fun repeatLabel(rule: String) = when {
        rule.isBlank() -> ""
        rule == "monthly" -> "每月"
        rule.startsWith("daily:") -> repeatEveryNDays(rule.removePrefix("daily:").toIntOrNull() ?: 1)
        rule.startsWith("weekly:") -> {
            val days = ruleDayList(rule.removePrefix("weekly:"))
            if (days.size == 7) "每天" else "每周" + dayNamesOf(days)
        }
        rule.startsWith("biweekly:") -> "隔周" + dayNamesOf(ruleDayList(rule.removePrefix("biweekly:")))
        else -> ""
    }

    private fun ruleDayList(body: String) =
        body.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }

    private fun dayNamesOf(days: List<Int>) =
        days.joinToString("/") { weekdayShort(it).removePrefix("周") }

    override fun deleteBody(name: String) = "「${name}」将被删除，此操作不可撤销。"
    override fun typeLabel(key: String) = when (key) {
        "interview" -> "面试"; "contest" -> "比赛"; "lecture" -> "讲座"
        "exam" -> "考试"; "meeting" -> "会议"; "other" -> "其他"
        else -> ""
    }

    override val pasteLabel = "粘贴通知文字，自动识别时间 / 地点 / 类型"
    override val pastePlaceholder = "例如：9月18日下午3点50分在C1-2003参加宣讲会"
    override val pasteBtn = "智能识别填充"
    override val parseOk = "已识别，请核对以下内容"
    override val parsePartial = "部分识别成功，请补充缺失项"
    override val parseFail = "未识别到有效信息，请手动填写"

    override fun ongoingHeader(n: Int) = "正在进行的长日程或长计划（${n}）"
    override val ongoingEmptyTitle = "暂无正在进行的长日程或长计划"
    override val ongoingEmptyHint =
        "长日程或长计划用于跨天的时间段（如考试报名 9/10 10:00 ~ 10/30 22:00）。" +
            "可在「日程表」或「计划」页点「添加」按钮，选择「长日程」或「长计划」创建。"
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "剩余 ${days}天${hours}小时"
        hours > 0 -> "剩余 ${hours}小时${mins}分"
        else -> "剩余 ${mins}分"
    }

    override val secAccount = "账号"
    override fun secAccountSubOf(schoolId: String, schoolName: String): String {
        // 学校专属称呼（若有）统一放在 SchoolTexts，本处不含任何学校 id 分支
        val sys = SchoolTexts.accountSystemName(schoolId, AppLang.ZH)
        return if (sys != null) "登录$sys" else "登录${schoolName}的教务系统"
    }
    override val labelStudentId = "学号"
    override val secProfile = "身份预设"
    override val secProfileSub =
        "填写学院/专业/年级/班级后，AI 助手遇到「多个班级 / 多个时间分支」的通知时，只添加与你相符的那一条"
    override val labelCollege = "学院"
    override val labelMajor = "专业"
    override val labelGrade = "年级"
    override val labelClazz = "班级"
    override val btnSaveProfile = "保存身份预设"
    override val aiDoneTitle = "AI 识别完成"
    override fun aiDoneBody(n: Int) = "识别出 $n 条，点此查看结果"
    override val aiFailedTitle = "AI 识别失败"
    override val channelAiName = "AI 识别结果"
    override val channelAiDesc = "识别完成后的浮动提醒（可单独关闭）"
    override val aiBadgeDesc = "有未查看的 AI 识别结果"
    override val labelPassword = "密码"
    override val labelPasswordKeep = "密码（已保存，留空表示不修改）"
    override val btnSaveAndSync = "保存并同步"
    override val btnLogout = "退出登录"
    override val btnClearCred = "删除保存的账密"
    override fun statusSaved(id: String) = "登录状态：已保存账号（${id}）"
    override val statusSession = "登录状态：会话有效（未保存密码）"
    override val statusNone = "登录状态：未登录"
    override fun lastSyncAt(time: String) = "上次同步：${time}"
    override val neverSynced = "从未同步"
    override val accountFootnote =
        "账号密码仅保存在本机（Android Keystore 加密），用于在统一身份认证页面自动登录。" +
            "「删除保存的账密」只清除本机存的学号密码（保留网页会话与课表缓存）；" +
            "「退出登录」则连同网页会话一起清除；如遇验证码等无法自动完成的情况，请稍后重试。"
    override val labelViaWebVpn = "通过 WebVPN"
    override val labelViaWebVpnSub =
        "勾选：通过 WebVPN 访问教务系统；取消：使用默认方式。两种方式各自独立实现，改动其一不影响另一。"
    override val askClearCredTitle = "删除保存的账密？"
    override val askClearCredBody = "将删除本机保存的学号与密码；网页登录会话与课表缓存保留。之后可随时重新输入保存。"

    override val secReminder = "课前提醒"
    override val secReminderSub = "在每节课开始前推送通知提醒"
    override val reminderEnable = "开启提醒"
    override val remindAgendaLabel = "日程/计划也提醒（有具体开始时间）"
    override val leadLabel = "提前时间"
    override fun minutes(n: Int) = "${n} 分钟"
    override val exactOk = "精确提醒：可用"
    override val exactNo = "精确提醒：未授权（将延迟最多 10 分钟）"
    override val gotoGrant = "去授权"
    override val batteryOk = "后台限制：已解除（提醒更可靠）"
    override val batteryNo = "后台限制：未解除（可能收不到提醒）"
    override val gotoExempt = "去解除"

    // 学校
    override val secSchool = "学校"
    override val secSchoolSub = "选择课表对应的学校"
    override val schoolChange = "切换"
    override fun msgSchoolSwitched(name: String) = "已切换到 $name"

    // 学校适配器（添加学校）
    override val schoolAdd = "添加学校"
    override val adapterName = "学校名称"
    override val adapterSite = "学校官网（可选）"
    override val adapterGen = "AI 生成适配代码"
    override val adapterCodeLabel = "适配代码（JSON 格式）"
    override val adapterTemplate = "复制格式模板"
    override val adapterCopied = "已复制格式模板"
    override val adapterSave = "保存并添加"
    override val adapterNeedName = "请填写学校名称"
    override fun adapterInvalid(msg: String) = "格式错误：$msg"
    override fun adapterAdded(name: String) = "已添加学校：$name"
    override val adapterExtraLabel = "补充线索（可选）"
    override val adapterExtraHint = "可粘贴教务系统地址、接口说明或页面片段，帮助 AI 生成更准确的适配代码"
    override val adapterWarnTitle = "该功能可用性存疑"
    override val adapterWarnBody =
        "不同教务系统的页面结构差异很大：通用适配器（含 AI 生成）往往无法直接可用，" +
            "生成后可能还需要您自己改脚本、并反复真机调试。入口保留供自行尝试，不保证一定成功。"
    override val adapterStageRequest = "正在请求 DeepSeek 生成适配代码…"
    override val adapterStageParse = "正在解析生成结果…"
    override val adapterStageDone = "已生成适配代码，请核对后保存"
    override fun adapterWaiting(sec: Int) = "（已等待 $sec 秒，请耐心等待）"

    // AI 助手（悬浮入口）
    override val qaTitle = "AI 助手"
    override val qaHint = "输入或粘贴内容：可添加新日程/计划，也可让 AI 修改、删除已有条目（如“把体检改到明天下午”）"
    override val qaParse = "AI 识别"
    override val qaConfirm = "全部执行"
    override val qaNothing = "未识别到可执行的操作"
    override val qaStaleResult = "内容已修改，本次识别结果已忽略，请重新识别"
    override val qaReparseTitle = "重新识别？"
    override val qaReparseMsg = "重新识别将清空当前预览列表（含已手动修改的内容）"
    override val opDateInvalid = "日期格式无效：请使用 2026-09-20 格式"
    override val discardTitle = "放弃未保存的修改？"
    override val discardOk = "放弃"
    override val msgExporting = "正在生成图片，请稍候…"
    override val qaPlanTag = "计划"
    override val qaAgendaTag = "日程"
    override fun qaAdded(n: Int) = "已添加 $n 项"
    override val opAdd = "添加"
    override val opUpdate = "修改"
    override val opDelete = "删除"
    override val opMatchTitle = "目标标题"
    override val opMatchDate = "目标日期（可选，如 2026-09-20）"
    override val opNewTitle = "新标题（留空不变）"
    override val opNewDate = "新日期（留空不变）"
    override val opNewStart = "新开始（留空不变）"
    override val opNewEnd = "新结束（留空不变）"
    override val opNewLocation = "新地点（留空不变）"
    override fun qaOpsDone(added: Int, updated: Int, deleted: Int, unmatched: Int): String {
        val parts = buildList {
            if (added > 0) add("已添加 $added 条")
            if (updated > 0) add("已修改 $updated 条")
            if (deleted > 0) add("已删除 $deleted 条")
            if (unmatched > 0) add("$unmatched 条未找到目标")
        }
        return parts.joinToString(" · ").ifBlank { "没有可执行的操作" }
    }
    override val timeExact = "具体时间"
    override val timeFuzzy = "模糊时间"
    override val timeFuzzyHint = "选择大致时段（凌晨 / 早晨 / 上午 / 下午 / 晚上 / 午夜）；此日程只按天提示倒计时"

    override val secOverlay = "悬浮球"
    override val secOverlaySub = "在任意应用上方显示AI助手悬浮球"
    override val overlayEnable = "开启悬浮球"
    override val overlayHint = "拖动悬浮球松开后会自动吸附到最近的屏幕侧边"
    override val overlayNeedPerm = "需要「显示在其他应用上层」权限"
    override val overlayGrant = "去授权"
    override val qaOpenInApp = "在应用中打开"
    override val overlayNotifTitle = "AI 悬浮球"
    override val overlayNotifText = "点按悬浮球即可输入文字并添加日程"
    override val overlayNotifStop = "关闭悬浮球"
    override val qaReParse = "内容已修改，请重新识别"

    // AI 识别（DeepSeek）
    override val secAi = "AI 识别（DeepSeek）"
    override val secAiSub = "配置 API Key 后，可用一句话添加、修改或删除日程/计划"
    override val aiKeyLabel = "DeepSeek API Key"
    override val aiKeyKeepHint = "已保存（留空则不修改）"
    override val aiUseDevKey = "使用开发者的 API key"
    override val aiDevKeyInUse = "已启用内置 API Key，无需自行申请即可使用 AI 识别"
    override val aiDevModelFixed = "使用开发者 Key 时固定为 deepseek-flash（不可修改）"
    override val aiModelLabel = "模型"
    override val aiSave = "保存 AI 设置"
    override val aiClear = "清除 Key"
    override val aiSavedToast = "AI 设置已保存（Key 加密存储于本机）"
    override val aiClearedToast = "已清除 AI Key"
    override val aiBtn = "AI 识别"
    override val aiRunning = "正在请求 AI…"
    override val aiOk = "AI 识别完成"
    override val aiNeedKey = "请先在 设置 → AI 识别 配置 DeepSeek API Key"

    // 常驻通知
    override val secStatus = "常驻通知"
    override val secStatusSub = "在锁屏与通知栏常驻显示（静默、内容可选）"
    override val statusEnable = "开启常驻通知"
    override val statusSrcCourse = "下一节课与当前课程"
    override val statusSrcPlan = "当前 / 下一计划"
    override val statusSrcAgenda = "下一日程"
    override val statusSrcAi = "AI 快速添加入口"
    override val statusHint = "可多选；选中项会常驻显示在锁屏与通知栏"
    override val statusChannelName = "状态常驻"
    override val statusChannelDesc = "锁屏与通知栏常驻显示课表/日程状态"
    override fun statusInClassFmt(name: String, end: String) = "正在上课：$name · 至 $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "下一节课：$name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "进行中计划：$name"
    override fun statusNextPlanFmt(name: String, time: String) = "下一计划：$name · $time"
    override fun statusNextAgendaFmt(name: String, time: String) = "下一日程：$name · $time"
    override val statusAiEntryTitle = "AI 助手"
    override val statusAiEntryHint = "点按输入文字 · 可添加 / 修改 / 删除日程"
    override val notifOk = "通知权限：已开启"
    override val notifNo = "通知权限：未开启"
    override val enableNotif = "开启通知"

    override val secData = "数据&图片"
    override val secDataSub = "课表从教务系统同步后缓存在本机"
    override val autoRefresh = "自动同步"
    override val autoCheckUpdate = "自动检查更新"
    override val fieldRepeatUntil = "重复截止"
    override val repeatUntilNone = "不限"
    override val tabSchool = "学校"
    override val comingSoon = "暂未完成"
    override val btnSyncNow = "立即同步"
    override val btnClearCache = "清除缓存"
    override val btnSaveDayImg = "保存日课表图片"
    override val btnSaveWeekImg = "保存周课表图片"
    override val btnSaveMonthImg = "保存月课表图片"
    override val settingsTabFeature = "功能&权限"
    override val settingsTabCustom = "用户自定义"
    override val settingsTabAccount = "学校&账号"
    override val settingsTabAccountSub = "选择学校、保存登录账号"
    override val settingsTabFeatureSub = "权限、课前提醒、AI、悬浮球、常驻通知、小组件"
    override val settingsTabCustomSub = "时间线显示范围、小组件刷新频率"
    override val secPermissions = "权限设置"
    override val secPermissionsSub = "按需开启；未授予时对应功能不可用（可随时在此查看状态）"
    override val permGranted = "已授予"
    override val permNotGranted = "未授予"
    override val permRequest = "去申请"
    override val permNetworkName = "网络访问"
    override val permNetworkWhy = "从教务系统同步课表（安装时即已具备，无需授权）"
    override val permNotifName = "通知权限"
    override val permNotifWhy = "课前提醒、常驻通知、测试通知"
    override val permOverlayName = "悬浮窗权限（显示在其他应用上层）"
    override val permOverlayWhy = "AI 悬浮球在其他应用上方显示"
    override val permExactName = "精确闹钟"
    override val permExactWhy = "提醒准点触发；未授予时会延迟最多 10 分钟"
    override val permBatteryName = "忽略电池优化"
    override val permBatteryWhy = "后台提醒更可靠"
    override val permStorageName = "存储权限（Android 8.0–9）"
    override val permStorageWhy = "导出课表图片到系统相册"
    override val permInstallName = "安装未知应用"
    override val permInstallWhy = "应用内「检查更新 → 下载并安装」需要它（不影响其他功能）"
    override fun permMissingCount(n: Int) = "未授予 $n 项"
    override val imgDirNote = "图片会保存到系统相册的 Pictures/K日程 目录。"

    override val secLang = "语言"
    override val secLangSub = "简体中文 / English / Français（默认跟随系统）"
    override val langSystem = "跟随系统"

    override val secDev = "开发者工具"
    override val secDevSub = "运行日志与诊断信息"
    override val openDevTools = "打开开发者工具"
    override val secDevBrowser = "内置浏览器"
    override val secDevBrowserSub = "普通浏览器（不注入脚本、不自动跳转），带访问记录，用于手动观察页面流程"
    override val btnOpenDevBrowser = "打开内置浏览器"
    override val devBrowserTitle = "内置浏览器"
    override val devBrowserAddress = "地址"
    override val devBrowserGo = "前往"
    override val devBrowserForward = "前进"
    override val devBrowserRefresh = "刷新"
    override val devBrowserHistory = "访问记录"
    override val devBrowserClear = "清空"
    override val devBrowserEmpty = "暂无访问记录"
    override val devBrowserClose = "关闭"
    override val secNotifTest = "通知测试"
    override val secNotifTestSub = "验证通知与提醒链路是否正常"
    override val sendTestNotifBtn = "立即发送测试通知"
    override val sendTestReminderBtn = "约 15 秒后发送测试提醒"
    override val testNotifSent = "已发送测试通知（若通知权限开启，应能在通知栏看到）"
    override val testReminderScheduled = "已安排测试提醒：约 15 秒后触发（未授权精确提醒时可能延后）"
    override val secReminderTools = "提醒调度工具"
    override fun reminderCountLabel(n: Int) = "当前已排提醒：$n 条"
    override val rescheduleBtn = "重新排程提醒"
    override val cancelAlarmsBtn = "取消全部提醒"
    override val rescheduled = "已重新排程提醒"
    override val alarmsCancelled = "已取消全部提醒"

    override val secReset = "重置"
    override val secResetSub = "清理数据或恢复初始状态"
    override val resetClearData = "清除日程与计划"
    override val resetClearDataDesc = "删除全部本地日程与计划；课程缓存与账号保留。"
    override val resetClearCache = "清除缓存与日志"
    override val resetClearCacheDesc = "删除课表缓存与运行日志。"
    override val resetLogoutDesc = "清除本机保存的学号密码、网页会话与内置浏览器状态；课表缓存与日程保留。"
    override val resetAll = "重置应用（恢复初始状态）"
    override val resetAllDesc = "将清除账号、网页会话、内置浏览器的缓存与本地存储、课表缓存、日程与计划、提醒与闹钟、日志及语言等设置，等价于刚安装好的状态。"
    override val askResetTitle = "确认重置？"
    override val askResetBody = "所有数据与设置将被清除，应用回到初始状态。"
    override val askClearDataTitle = "清除全部日程与计划？"
    override val askClearDataBody = "本地日程与计划将被全部删除，此操作不可撤销。"
    override val askClearCacheTitle = "清除缓存与日志？"
    override val askClearCacheBody = "将删除课表缓存与运行日志。"

    override val secAbout = "关于"
    override val settingsTabAboutFeedback = "关于&反馈"
    override val aboutSub = "K日程 ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "本地优先的课表与日程应用：课表从学校教务系统同步后缓存在本机，" +
            "支持日/周/月视图（左右滑动切换）、日程与计划、课前提醒、桌面小组件与常驻通知；" +
            "内置 DeepSeek AI 助手（可添加/修改/删除日程，入口：顶栏按钮或悬浮球）。" +
            "除用户自行配置的 AI 接口外，不上传任何个人信息。"
    override val aboutTip = "提示：若同步失败而网页可正常访问，多为学校页面改版——北航可到「开发者工具」分享运行日志；其他学校可在「设置 → 学校 → 添加学校」更新适配代码。"
    override val aboutPeriods = "课程节数与时间均可自定义：默认 14 节（上午 4 + 下午 5 + 晚上 5），可在「设置 → 用户自定义 → 课程时间」中增删节次并修改起止时间。课程信息已经变了但教务系统还没更新时，可在课程详情里用「修改课程」做本地修正（支持「仅这一次」与「全部同一课程」），设置里可一键还原或清除后重新同步。"

    override val updateCheck = "检查更新"
    override fun updateCurrent(name: String) = "当前版本：$name"
    override val updateChecking = "正在检查更新…"
    override val updateLatest = "已是最新版本"
    override fun updateAvailable(name: String, size: String) = "发现新版本 $name（$size）"
    override val updateDownloadAndInstall = "下载并安装"
    override val updateInstall = "安装"
    override fun updateDownloading(percent: Int) = "正在下载… $percent%"
    override fun updateDownloaded(size: String) = "更新包已下载（$size），点此安装"
    override fun updateStartDownload(size: String) = "开始下载更新包（$size）"
    override fun updateFailed(msg: String) = "更新失败：$msg"
    override val updateHintAvailable = "(有更新!)"
    override fun updateHintDownloading(percent: Int) = "(下载中 $percent%)"
    override val updateHintInstall = "(点击安装)"
    override val updateSizeUnknown = "大小未知"
    override val updatePermissionTitle = "需要「安装未知应用」权限"
    override val updatePermissionBody =
        "为安装新版 K日程，请在系统设置里允许本应用「安装未知应用」，然后返回重试。"
    override val updateOpenSettings = "去设置"
    override val updatePackageInvalid = "更新包不可用，请重新下载"

    override val logTitle = "运行日志"
    override val logDesc = "日志记录每次同步的阶段、页面状态与错误信息（不包含密码），可直接分享给开发者排查。"
    override val viewShareLog = "查看 / 分享日志"
    override val logFilterAll = "全部"
    override val logFilterWarn = "警告+"
    override val logFilterError = "仅错误"
    override val clearLogBtn = "清空日志"
    override val readingLog = "正在读取日志…"
    override val emptyLog = "（暂无日志）"
    override val logShareSubject = "K日程运行日志"
    override val shareChooseFormat = "分享日志"
    override val shareFormatHint = "将生成一个日志文件（英文文件名 + 时间点）后分享，请选择文件格式："
    override val shareAsTxt = "TXT 文本"
    override val shareAsMd = "Markdown"
    override val logMdTime = "生成时间"
    override val logMdVersion = "应用版本"
    override val logMdDevice = "设备"
    override val diagTitle = "诊断信息"
    override val diagVersion = "应用版本"
    override val diagSync = "同步状态"
    override val diagLastSync = "最近同步"
    override val diagNone = "无"

    override val msgNeedStudentId = "请输入学号"
    override val msgAccountSaved = "账号已保存，正在同步…"
    override val msgAccountSavedKeepPwd = "已保存学号（未输入新密码：使用已保存的密码同步，不校验密码）"
    override val msgLoggedOut = "已退出登录：账号与网页会话已清除"
    override val msgCredDeleted = "已删除本地保存的学号密码（网页会话与课表缓存保留）"
    override val msgResetDone = "已恢复初始状态：请重新登录"
    override val msgEventsCleared = "日程与计划已清除"
    override val msgCacheLogsCleared = "缓存与日志已清除"
    override val msgCacheCleared = "已清除本地课表缓存"
    override val msgReminderOn = "已开启课前提醒"
    override val msgReminderOff = "已关闭课前提醒"
    override val msgNoScheduleData = "还没有课表数据，请先同步"
    override val msgSaveFailed = "保存失败，请重试"
    override val msgStorageNeedPerm = "未授予存储权限，无法保存到相册（Android 8.0–9 需要）"
    override val msgSavedDayImage = "日课表已保存到相册（Pictures/K日程）"
    override val msgSavedWeekImage = "周课表已保存到相册（Pictures/K日程）"
    override val msgSavedMonthImage = "月课表已保存到相册（Pictures/K日程）"

    override val channelName = "上课提醒"
    override val channelDesc = "上课前提醒通知"
    override fun notifClassSoon(title: String) = "即将上课：${title}"
    override fun notifAgendaSoon(title: String) = "即将开始：${title}"
    override val syncTimeLabel = "数据同步时间："
    override val exportTimeLabel = "导出时间："

    override val backToToday = "回到今天"
    override val backToThisWeek = "回到本周"

    override val secPeriodTimes = "课程时间"
    override val secPeriodTimesSub = "自定义每一节课的起止时间（默认与学校作息一致，共 14 节）"
    override val btnEditPeriodTimes = "自定义课程时间"
    override val periodTimesTitle = "课程时间"
    override val periodTimesHint = "节数可增删（1–24 节）；时间按 24 小时制填写 HH:mm（也支持 800 / 0800 / 8.00）。修改后日/周视图、小组件与常驻通知立即更新。"
    override val periodTimesRestoreDefault = "恢复默认"
    override val periodTimesRowFmt = "第%d节"
    override fun periodTimesCount(n: Int) = "当前共 $n 节"
    override fun periodTimesCountWarning(maxUsed: Int, configured: Int) =
        "注意：当前课表最晚到第 $maxUsed 节，而这里只配置了 $configured 节——超出的课程不会显示在周视图中。"
    override val periodTimesAddOne = "＋ 添加一节"
    override val periodTimesRemoveOne = "删除该节"
    override val msgPeriodTimesSaved = "课程时间已更新"
    override val msgPeriodTimesInvalid = "时间格式不正确，请按 HH:mm 填写全部节次"
    override val periodTimesDefaultTag = "默认作息"
    override val periodTimesCustomTag = "自定义作息"

    override val secLockScreen = "锁屏显示"
    override val secLockScreenSub = "关闭后常驻通知不会出现在锁屏上"
    override val btnOpenNotifSettings = "打开系统通知设置"
    override val lockScreenHint = "锁屏上看不到通知？部分系统（如 MIUI/HyperOS、EMUI）会默认隐藏静默通知；可在系统通知设置里为本应用开启「锁屏通知」。"

    override val secUiStyle = "界面风格"
    override val secUiStyleSub = "选择整体视觉风格（默认保留原样式）"
    override val uiStyleDefault = "默认(简约)"
    override val uiStyleDefaultSub = "当前的不透明卡片样式"
    override val uiStyleGlass = "液态玻璃"
    override val uiStyleGlassSub = "渐变背景 + 半透明毛玻璃卡片，圆角更柔和"

    override val secDocs = "文档导入"
    override val secDocsSub = "读取 Word / Excel / PPT / PDF / 文本，自动识别课程表、日程与计划"
    override val docTitle = "文档导入"
    override val docIntro = "选择本地文档，应用会在本机提取文字并自动识别课程表、日程与计划（不上传任何内容）。"
    override val docOpenHint = "在文件管理或其他应用里选择「用 K日程 打开」也能直接进入本页。"
    override val docPick = "选择文档"
    override val docReading = "正在读取文档…"
    override fun docReadFail(msg: String) = "读取失败：$msg"
    override fun docUnsupportedExt(ext: String) = "暂不支持 .$ext 格式（可另存为 docx / xlsx / pdf / txt 后重试）"
    override val docNoText = "未从文档中提取到文字（扫描版 PDF / 图片型文档需要手动粘贴文字）"
    override val docTextLabel = "文档文字（可修改后重新识别）"
    override fun docDetected(events: Int, courses: Int) = "已识别：$events 条日程/计划，$courses 门课程"
    override val docImportAgenda = "导入为日程"
    override val docImportPlan = "导入为计划"
    override val docImportCourses = "导入为课程表"
    override fun docAnchorLabel(date: String) = "第 1 教学周周一：$date"
    override val docAnchorHint = "课程周次按该日期换算，与本学期同步数据保持一致；可左右微调"
    override fun docImportedList(n: Int) = "已导入课程（$n）"
    override val docClearImported = "清空导入课程"
    override val docExport = "导出为文档"
    override val docExportHint = "把当前课表与日程导出为 CSV / Markdown 文件（存到你选择的位置）"
    override val docExportCsv = "导出 CSV"
    override val docExportMd = "导出 Markdown"
    override val docExported = "已导出到所选位置"
    override val docExportFailed = "导出失败"
    override fun msgImportedCourses(n: Int) = "已导入 $n 门课程"
    override val msgImportedCoursesCleared = "已清空导入课程"
    override val msgImportNoCourses = "未识别到课程，请确认文档里有「星期 + 节次」信息"
    override val msgImportNoEvents = "未识别到日程或计划"
    override val docLocalOnly = "全部在本机完成，不发送到网络"

    override val aiOfflineParse = "本地识别"
    override fun aiOfflineDone(n: Int) = "本地识别到 $n 条（未调用 AI）"

    // ---------------- 课程修改 ----------------
    override val tagEdited = "已修改"
    override val btnEditCourse = "修改课程"
    override val courseEditTitle = "修改课程"
    override val courseEditIntro =
        "用于教务系统还没更新、但课程信息已经变了的情况：修改只保存在本机，不会改动教务系统。"
    override val editScopeLabel = "修改范围"
    override val editScopeOne = "仅修改这一次"
    override val editScopeOneHint = "只改动这一个课程块（星期与节次也可以一起调整）"
    override val editScopeAll = "修改全部同一课程"
    override val editScopeAllHint = "这门课在各个星期/节次的安排都会套用这次修改"
    override val fieldCourseName = "课程名"
    override val editWeeksHint = "如 2-17；留空表示每周"
    override val editPeriodStart = "起始节次"
    override val editPeriodEnd = "结束节次"
    override val editNoChange = "没有检测到修改"
    override val editSavedNote = "已在本机修改，尚未与教务系统同步"
    override val msgCourseEditSaved = "已保存课程修改，并标记为「已修改」"
    override val msgImportedCourseEdited = "已修改导入的课程"
    override val msgCourseEditsRestored = "已还原全部课程修改"
    override val msgCourseEditsClearedSyncing = "已清除课程修改，正在与教务系统同步…"
    override val msgEditFollowed = "已改为跟随教务系统"
    override val msgEditKept = "已保留你的修改"
    override fun msgEditConflicts(n: Int) = "有 $n 门课程的修改与教务系统不一致，请确认"

    override val secCourseEdits = "课程修改"
    override val secCourseEditsSub = "教务系统还没更新时的本地修正"
    override fun courseEditsCount(n: Int) = "$n 门已修改"
    override val courseEditsNone = "暂无课程修改"
    override val courseEditsIntro =
        "点课表里的课程卡片即可修改课程信息；教务系统之后同步到同样内容时，「已修改」标记会自动消失。"
    override val btnRestoreCourseEdits = "仅还原课程修改"
    override val btnSyncClearCourseEdits = "与教务系统同步并清除所有课程修改"
    override val courseEditsHint =
        "「仅还原」只把课表恢复成教务系统的原样（不联网）；「同步并清除」会先清掉全部修改，再重新拉取教务课表。"
    override val askRestoreEditsTitle = "还原全部课程修改？"
    override val askRestoreEditsBody = "全部课程会恢复为教务系统保存的原样，「已修改」标记一并消失。"
    override val askSyncClearEditsTitle = "与教务系统同步并清除修改？"
    override val askSyncClearEditsBody = "将清除全部本地课程修改并重新同步课表，教务系统的数据会直接生效。"

    override val editConflictTitle = "课程信息与教务系统不一致"
    override val editConflictIntro = "教务系统更新了这些课程，与你之前修改的内容也不同。是否改为跟随教务系统？"
    override val editConflictLatest = "教务系统最新"
    override val editConflictYours = "你的修改"
    override val editConflictMissing = "教务系统中已找不到这门课"
    override val btnFollowAcademic = "跟随教务系统"
    override val btnKeepMine = "保留我的修改"
    override val btnFollowAll = "全部跟随教务系统"
    override val btnKeepAll = "全部保留我的修改"
    override val editConflictLater = "稍后处理"
    // ---------------- 手动改课表：删除 / 新增 / 调课（调休） ----------------
    override val editActionModify = "修改信息"
    override val editActionDelete = "删除课程"
    override val editActionMove = "调课"
    override val editDeleteIntro =
        "选中的周次里不再显示这门课（只在本机生效，不会改动教务系统）。"
    override val editDeleteWeeksHint = "全选 = 整门课都隐藏"
    override fun editMoveIntro(n: Int) = "把这一天的课整体或单门迁到其他日期（只在本机生效）。这一天共 $n 门课。"
    override val editMoveWholeDay = "整天的课一起调"
    override fun editMoveWholeDayHint(n: Int) = "这一天全部 $n 门课都迁到目标日期"
    override val editMoveSingle = "只调某几节"
    override val editMoveSingleHint = "勾选要迁过去的课（可多选），其余不动"
    override val editMoveTarget = "目标日期"
    override val editMovePickDate = "选择日期"
    override val weeksAll = "全选"
    override val weeksNone = "清空"
    override val courseAddTitle = "新增课程"
    override val courseAddIntro =
        "教务系统里没有的课可以在这里本地添加（只在课表里显示，不会上传）。"
    override val btnAddCourse = "添加课程"
    override val btnCopyCourse = "复制课程"
    override val aboutOfficialSite = "官方网站 / 下载页"
    override val aboutGithubRepo = "GitHub 仓库"

    override val secFeedback = "用户反馈"
    override val secFeedbackSub = "问题、建议或课表适配需求"
    override val feedbackIntro =
        "反馈会提交到开发者站点（20071009.xyz）。请勿填写账号密码等敏感信息。"
    override val feedbackTopic = "反馈主题"
    override val topicBug = "问题反馈"
    override val topicFeature = "功能建议"
    override val topicSchool = "课表适配"
    override val topicOther = "其他"
    override val feedbackPlaceholder = "请描述遇到的问题或想要的功能…"
    override val feedbackContact = "联系方式（选填）"
    override val btnFeedbackSend = "提交反馈"
    override val msgFeedbackSent = "反馈已提交，谢谢！"
    override val msgFeedbackFailed = "提交失败，请检查网络后重试"
    override val feedbackEmpty = "请先填写反馈内容"
    override val feedbackIncludeNote =
        "提交时会附带：应用版本、Android 版本、当前学校（不含学号、密码、课表内容）"
    override val btnRescheduleDate = "调整至..."
    override val longPressHint = "长按日期可调整至其他日期"
    override val rescheduleTitle = "调整至..."
    override fun rescheduleIntro(month: Int, day: Int, n: Int) =
        "$month/$day 共有 $n 门课。选择要迁移的课与目标日期。"
    override val rescheduleSingleHint = "只调某一节（在下方选择）"
    override val msgCourseDeleted = "已删除（仅本机生效）"
    override val msgCourseAdded = "已添加课程（仅本机生效）"
    override fun msgCourseMoved(n: Int, month: Int, day: Int) = "已把 $n 门课调整到 $month/$day"
    override val msgMoveSameDay = "目标日期与原来相同"
    override val msgMoveNoWeek = "无法确定教学周，请先同步一次课表"

    override val secHolidayCourses = "节假日显示课表"
    override val secHolidayCoursesSub = "默认停课（不显示节假日当天的课）"
    override val holidayCoursesSwitch = "节假日照常显示课表"
    override val holidayCoursesNote =
        "开启后，法定节假日当天的课会照常显示、提醒也会照常触发（方便查看与调休节假日课程）。"

    override val secBackup = "数据备份与导入"
    override val secBackupSub = "换机 / 重装前先导出一份"
    override val backupIntro =
        "导出一个备份文件（课表缓存、日程与计划、课程修正、全部设置），重装或换机后可导入恢复。"
    override val backupIncludes =
        "备份包含学号、课表、日程与设置；不含登录密码与 API Key（换机后无法解密，需重新输入）。"
    override val btnBackupExport = "导出备份"
    override val btnBackupImport = "导入备份"
    override val backupAskImportTitle = "导入备份？"
    override val backupAskImportBody =
        "将用备份里的内容覆盖当前的课表、日程与设置（本机密码与 API Key 不受影响）。"
    override val backupFileName = "KAgenda_backup"
    override val backupExportTitle = "导出备份到"
    override val backupExportDefault = "默认位置（推荐）"
    override val backupExportCustom = "选择其他位置..."
    override fun msgBackupExportedDefault(dir: String, name: String) = "备份已保存到 $dir/$name"
    override val backupImportTitle = "导入备份"
    override fun backupImportFound(n: Int, dir: String) = "在 $dir 找到 $n 份备份："
    override val backupImportPickOther = "选择其他文件..."
    override fun backupDefaultDirNote(dir: String) =
        "默认位置：$dir（卸载重装后仍会保留；导入时优先检查这里）"
    override fun msgBackupExported(size: String) = "备份已导出（$size）"
    override val msgBackupExportFailed = "备份导出失败"
    override fun msgBackupImported(agenda: Int, edits: Int, courses: Int) =
        "备份已导入：日程/计划 $agenda 条，课程修正 $edits 条，导入课程 $courses 门"
    override val msgBackupInvalid = "这不是 KAgenda 的备份文件"
    override fun msgBackupNewer(version: Int) = "备份格式版本（$version）比当前应用新，请先更新应用"
    override fun msgBackupFailed(detail: String) = "导入失败：$detail"
}

// ==================================================================== English

object EnStrings : AppStrings {
    override val appTitle = "K Agenda"
    override val brand = "K Agenda"
    override val save = "Save"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val confirm = "OK"
    override val back = "Back"
    override val refresh = "Refresh"
    override val share = "Share"

    override val syncSyncedAt = "Synced · "
    override val syncNotLoggedIn = "Not signed in"
    override val syncRunning = "Syncing…"
    override val syncHintNeedLogin = "Not signed in yet. Enter your student ID and password in Settings, then sync."
    override val syncHintRunning = "Fetching schedule from the academic system…"
    override val noScheduleTitle = "No schedule data"
    override val noScheduleHintDay = "Sign in with your student ID in Settings first, then tap refresh at the top right to fetch your schedule."
    override val noScheduleHintWeek = "Sign in and sync in Settings first to view the weekly schedule."
    override val noCoursesToday = "No classes on this day"
    override val noCoursesTodayHint = "Switch to another date, or tap refresh to sync the latest data."
    override val dayNoCoursesImage = "No classes scheduled this day 🎉"

    override val tabSchedule = "Schedule"
    override val tabPlan = "Plans"
    override val tabOngoing = "Ongoing"
    override val tabSettings = "Settings"
    override val viewDay = "Day"
    override val viewWeek = "Week"
    override val viewMonth = "Month"
    override val timetableMode = "Timetable mode"

    override val prevWeek = "Previous week"
    override val nextWeek = "Next week"
    override val prevMonth = "Previous month"
    override val nextMonth = "Next month"
    override val backToThisMonth = "This month"
    override val today = "Today"
    override val saveDaySchedule = "Save day"
    override val saveWeekSchedule = "Save week"
    override val saveMonthSchedule = "Save month"

    override fun weekNo(n: Int) = "Week ${n}"
    override fun weekdayShort(i: Int) =
        arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[(i - 1).coerceIn(0, 6)]
    override fun weekdayNarrow(i: Int) =
        arrayOf("M", "T", "W", "T", "F", "S", "S")[(i - 1).coerceIn(0, 6)]
    override fun monthTitle(year: Int, month: Int) =
        arrayOf("January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December")[(month - 1).coerceIn(0, 11)] + " " + year
    override fun dayImageTitle(month: Int, day: Int, dow: Int) =
        arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[(month - 1).coerceIn(0, 11)] +
            " ${day}, " + weekdayShort(dow)

    override fun monthImageTitle(year: Int, month: Int) = "Monthly Schedule · ${monthTitle(year, month)}"
    override val weekImageTitle = "Weekly Schedule"

    override fun periodNo(n: Int) = "Period ${n}"
    override fun periodNoShort(n: Int) = "#${n}"
    override fun periodsRange(a: Int, b: Int) = "Periods ${a}-${b}"
    override fun courseCount(n: Int) = "${n} classes"
    override fun eventCount(n: Int) = "${n} events"
    override fun weeksValue(raw: String) = "Weeks ${raw}"
    override val detailTime = "Time"
    override val detailWeekday = "Day"
    override val detailTeacher = "Teacher"
    override val detailRoom = "Room"
    override val detailWeeks = "Weeks"
    override val detailCode = "Course code"
    override val tagOngoing = "Ongoing"

    override val myAgenda = "My events"
    override val myAgendaWeek = "My events (this week)"
    override val addAgendaBtn = "＋ Add event"
    override val emptyAgendaHint = "No events yet. Tap \"＋ Add event\" to create one."
    override val agendaSaved = "Event saved"
    override val agendaDeleted = "Event deleted"
    override val myPlans = "My plans"
    override val myPlansWeek = "My plans (this week)"
    override val addPlanBtn = "＋ Add plan"
    override val emptyPlansHint = "No plans yet. Tap \"＋ Add plan\" to create one."
    override val planSaved = "Plan saved"
    override val planDeleted = "Plan deleted"
    override val mixedTitle = "Today (classes + events)"
    override val tagLong = "Long event"
    override val tagAgenda = "Event"
    override val tagPlan = "Plan"

    override val editorAddAgenda = "Add event"
    override val editorEditAgenda = "Edit event"
    override val editorAddPlan = "Add plan"
    override val editorEditPlan = "Edit plan"
    override val fieldTitle = "Title"
    override val labelColor = "Color"
    override val colorAuto = "Auto"
    override val segShort = "Single day"
    override val segLong = "Long term"
    override val segShortPlan = "Short plan"
    override val segLongPlan = "Long plan"
    override val secTimelineRange = "Timeline range"
    override val secTimelineRangeSub = "Start/end of the week timeline (non-timetable mode). An end earlier than the start wraps to the next day (default 06:00 – 02:00 next day)"
    override val secWeekDays = "Week view days"
    override val secWeekDaysSub = "How many days the week view shows by default; shared by Schedule and Plans (pinch to zoom)"
    override val weekDaysFive = "5 days (Mon–Fri)"
    override val weekDaysSeven = "7 days (Mon–Sun)"
    override val labelNextDay = "next day"
    override val secWidget = "Widget"
    override val secWidgetSub = "Pin a timetable widget to the home screen"
    override val secWidgetRefresh = "Widget refresh rate"
    override val secWidgetRefreshSub = "Refreshes more often as the next item approaches; defaults to 1 / 5 / 60 min"
    override val widgetRefreshCustom = "Custom refresh rate"
    override val widgetRefreshCustomOn = "Custom"
    override val widgetRefreshDefaultTag = "Default"
    override val widgetRefreshDefaultNote =
        "Default rate: every 1 min within 1 h of the next item; every 5 min within 3 h; every 60 min otherwise. Turn on the switch above to customize."
    override val widgetRefreshNear = "Within 1 h of the next item"
    override val widgetRefreshSoon = "Within 3 h"
    override val widgetRefreshFar = "Further / nothing scheduled"
    override val widgetPinHint = "Tap a size to pin it; if your launcher doesn't support in-app pinning, long-press the home screen and add K Agenda from the widget list"
    override val widgetPinUnsupported = "In-app pinning isn't supported by your launcher — long-press the home screen and add K Agenda from the widget list"
    override val fieldDate = "Date"
    override val fieldStartTime = "Start time"
    override val fieldEndTime = "End time"
    override val fieldStartDate = "Start date"
    override val fieldEndDate = "End date"
    override val unsetTime = "Not set"
    override val fieldLocation = "Location (optional)"
    override val fieldNote = "Note (optional)"
    override val errTitle = "Please enter a title"
    override val errEndBeforeStart = "End time cannot be earlier than start time"
    override val askDeleteAgenda = "Delete event?"
    override val askDeletePlan = "Delete plan?"
    override val pickerTimeTitle = "Select time"
    override val repeatSection = "Repeat"
    override val repeatNone = "No repeat"
    override val repeatDailyMode = "Every N days"
    override val repeatWeeklyMode = "Weekly"
    override val repeatBiweeklyMode = "Every other week"
    override val repeatMonthlyMode = "Monthly"
    override val repeatFieldHint = "e.g. Tue/Thu/Sat weekly, every other Tue, every 3 days (blank = unchanged)"
    override val errRepeatNoDay = "No weekday selected: pick at least one weekday to repeat"
    override val repeatNeedDayHint = "Pick at least one weekday (cannot save otherwise)"
    override fun repeatEveryNDays(n: Int) = if (n <= 1) "Daily" else "Every $n days"
    override fun repeatLabel(rule: String) = when {
        rule.isBlank() -> ""
        rule == "monthly" -> "Monthly"
        rule.startsWith("daily:") -> repeatEveryNDays(rule.removePrefix("daily:").toIntOrNull() ?: 1)
        rule.startsWith("weekly:") -> {
            val days = ruleDayList(rule.removePrefix("weekly:"))
            if (days.size == 7) "Every day" else "Every " + dayNamesOf(days)
        }
        rule.startsWith("biweekly:") -> "Every other week · " + dayNamesOf(ruleDayList(rule.removePrefix("biweekly:")))
        else -> ""
    }

    private fun ruleDayList(body: String) =
        body.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }

    private fun dayNamesOf(days: List<Int>) = days.joinToString("/") { weekdayShort(it) }

    override fun deleteBody(name: String) = "\"${name}\" will be deleted. This cannot be undone."
    override fun typeLabel(key: String) = when (key) {
        "interview" -> "Interview"; "contest" -> "Contest"; "lecture" -> "Lecture"
        "exam" -> "Exam"; "meeting" -> "Meeting"; "other" -> "Other"
        else -> ""
    }

    override val pasteLabel = "Paste a notice — time / location / type are detected automatically"
    override val pastePlaceholder = "e.g. Sep 18, 3:50 pm, lecture at C1-2003"
    override val pasteBtn = "Recognize & fill"
    override val parseOk = "Recognized — please review"
    override val parsePartial = "Partially recognized — complete the missing fields"
    override val parseFail = "Could not recognize — please fill in manually"

    override fun ongoingHeader(n: Int) = "Ongoing long events / plans (${n})"
    override val ongoingEmptyTitle = "No ongoing long events or plans"
    override val ongoingEmptyHint =
        "Long events or plans span multiple days (e.g. exam registration 9/10 10:00 – 10/30 22:00). " +
            "Create one from Schedule or Plans with \"Long term\" / \"Long plan\" selected."
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${mins}m left"
        else -> "${mins}m left"
    }

    override val secAccount = "Account"
    override fun secAccountSubOf(schoolId: String, schoolName: String): String {
        // 学校专属称呼（若有）统一放在 SchoolTexts，本处不含任何学校 id 分支
        val sys = SchoolTexts.accountSystemName(schoolId, AppLang.EN)
        return if (sys != null) "Sign in to $sys" else "Sign in to $schoolName's academic system"
    }
    override val labelStudentId = "Student ID"
    override val secProfile = "Identity profile"
    override val secProfileSub =
        "Fill in your college/major/grade/class: when a notice lists several classes or time slots, the AI adds only the one matching you"
    override val labelCollege = "College"
    override val labelMajor = "Major"
    override val labelGrade = "Grade"
    override val labelClazz = "Class"
    override val btnSaveProfile = "Save profile"
    override val aiDoneTitle = "AI recognition finished"
    override fun aiDoneBody(n: Int) = "$n item(s) found — tap to view"
    override val aiFailedTitle = "AI recognition failed"
    override val channelAiName = "AI results"
    override val channelAiDesc = "Floating alert when recognition finishes (can be muted separately)"
    override val aiBadgeDesc = "Unseen AI recognition result"
    override val labelPassword = "Password"
    override val labelPasswordKeep = "Password (saved — leave blank to keep)"
    override val btnSaveAndSync = "Save & sync"
    override val btnLogout = "Sign out"
    override val btnClearCred = "Delete saved credentials"
    override fun statusSaved(id: String) = "Signed in: account saved (${id})"
    override val statusSession = "Signed in: session active (no saved password)"
    override val statusNone = "Not signed in"
    override fun lastSyncAt(time: String) = "Last sync: ${time}"
    override val neverSynced = "Never"
    override val accountFootnote =
        "Your credentials are stored only on this device (Android Keystore encrypted) and used to sign in automatically. " +
            "\"Delete saved credentials\" removes only the local ID & password (web session and cache are kept); " +
            "\"Sign out\" clears the web session as well. If a captcha blocks auto sign-in, please retry later."
    override val labelViaWebVpn = "Via WebVPN"
    override val labelViaWebVpnSub =
        "Checked: reach the academic system through WebVPN. Unchecked: use the default route. The two routes are implemented separately and do not affect each other."
    override val askClearCredTitle = "Delete saved credentials?"
    override val askClearCredBody = "The local student ID and password will be deleted; web session and schedule cache are kept. You can save them again anytime."

    override val secReminder = "Class reminder"
    override val secReminderSub = "Get a notification before each class"
    override val reminderEnable = "Enable reminders"
    override val remindAgendaLabel = "Also remind for events/plans (with a set start time)"
    override val leadLabel = "Lead time"
    override fun minutes(n: Int) = "${n} min"
    override val exactOk = "Exact alarms: available"
    override val exactNo = "Exact alarms: not authorized (may delay up to 10 min)"
    override val gotoGrant = "Grant"
    override val batteryOk = "Battery: unrestricted"
    override val batteryNo = "Battery: restricted (reminders may not arrive)"
    override val gotoExempt = "Exempt"

    // School
    override val secSchool = "School"
    override val secSchoolSub = "Choose the school for your schedule"
    override val schoolChange = "Change"
    override fun msgSchoolSwitched(name: String) = "Switched to $name"

    // 学校适配器（添加学校）
    override val schoolAdd = "Add school"
    override val adapterName = "School name"
    override val adapterSite = "Website (optional)"
    override val adapterGen = "Generate adapter with AI"
    override val adapterCodeLabel = "Adapter code (JSON)"
    override val adapterTemplate = "Copy template"
    override val adapterCopied = "Template copied"
    override val adapterSave = "Save & add"
    override val adapterNeedName = "Enter a school name"
    override fun adapterInvalid(msg: String) = "Invalid format: $msg"
    override fun adapterAdded(name: String) = "School added: $name"
    override val adapterExtraLabel = "Extra context (optional)"
    override val adapterExtraHint = "Paste the academic-system URL, API notes or page snippets to help the AI generate a better adapter"
    override val adapterWarnTitle = "This feature may not work"
    override val adapterWarnBody =
        "Academic systems differ a lot in page structure: a generic adapter (including AI-generated ones) " +
            "often does not work out of the box — you may still need to edit the scripts and debug on a real device. " +
            "The entry is kept for you to try at your own risk; success is not guaranteed."
    override val adapterStageRequest = "Requesting DeepSeek to generate the adapter…"
    override val adapterStageParse = "Parsing the generated code…"
    override val adapterStageDone = "Adapter generated — review and save"
    override fun adapterWaiting(sec: Int) = " (waiting ${sec}s…)"

    // AI assistant (overlay entry)
    override val qaTitle = "AI Assistant"
    override val qaHint = "Type or paste: add new events/plans, or ask AI to update/delete existing ones (e.g. \"move the checkup to tomorrow afternoon\")"
    override val qaParse = "AI parse"
    override val qaConfirm = "Run all"
    override val qaNothing = "Nothing to run"
    override val qaStaleResult = "Text changed — result ignored. Please recognize again."
    override val qaReparseTitle = "Recognize again?"
    override val qaReparseMsg = "Re-recognizing will clear the current preview list (including manual edits)."
    override val opDateInvalid = "Invalid date — please use the format 2026-09-20"
    override val discardTitle = "Discard unsaved changes?"
    override val discardOk = "Discard"
    override val msgExporting = "Generating image, please wait…"
    override val qaPlanTag = "Plan"
    override val qaAgendaTag = "Event"
    override fun qaAdded(n: Int) = "Added $n item(s)"
    override val opAdd = "Add"
    override val opUpdate = "Update"
    override val opDelete = "Delete"
    override val opMatchTitle = "Target title"
    override val opMatchDate = "Target date (optional, e.g. 2026-09-20)"
    override val opNewTitle = "New title (blank = keep)"
    override val opNewDate = "New date (blank = keep)"
    override val opNewStart = "New start (blank = keep)"
    override val opNewEnd = "New end (blank = keep)"
    override val opNewLocation = "New location (blank = keep)"
    override fun qaOpsDone(added: Int, updated: Int, deleted: Int, unmatched: Int): String {
        val parts = buildList {
            if (added > 0) add("$added added")
            if (updated > 0) add("$updated updated")
            if (deleted > 0) add("$deleted deleted")
            if (unmatched > 0) add("$unmatched unmatched")
        }
        return parts.joinToString(" · ").ifBlank { "Nothing to run" }
    }
    override val timeExact = "Exact time"
    override val timeFuzzy = "Rough time"
    override val timeFuzzyHint = "Pick a rough period (凌晨 / 早晨 / 上午 / 下午 / 晚上 / 午夜); countdown is day-based"

    override val secOverlay = "Floating ball"
    override val secOverlaySub = "Show the AI assistant floating ball over any app"
    override val overlayEnable = "Enable floating ball"
    override val overlayHint = "Release the ball to snap it to the nearest screen edge"
    override val overlayNeedPerm = "Requires the \"Display over other apps\" permission"
    override val overlayGrant = "Grant"
    override val qaOpenInApp = "Open in app"
    override val overlayNotifTitle = "AI floating ball"
    override val overlayNotifText = "Tap the ball to type and add schedules"
    override val overlayNotifStop = "Turn off"
    override val qaReParse = "Text changed — tap AI parse again"

    // AI (DeepSeek)
    override val secAi = "AI recognition (DeepSeek)"
    override val secAiSub = "With an API key, add, edit or delete events/plans in natural language"
    override val aiKeyLabel = "DeepSeek API Key"
    override val aiKeyKeepHint = "Saved (leave blank to keep)"
    override val aiUseDevKey = "Use developer's API key"
    override val aiDevKeyInUse = "Built-in API key enabled — AI works out of the box"
    override val aiDevModelFixed = "Fixed to deepseek-flash when using the developer key"
    override val aiModelLabel = "Model"
    override val aiSave = "Save AI settings"
    override val aiClear = "Clear key"
    override val aiSavedToast = "AI settings saved (key encrypted on device)"
    override val aiClearedToast = "AI key cleared"
    override val aiBtn = "AI parse"
    override val aiRunning = "Calling AI…"
    override val aiOk = "AI parse done"
    override val aiNeedKey = "Configure the DeepSeek API key in Settings → AI first"

    // Persistent notification
    override val secStatus = "Persistent notification"
    override val secStatusSub = "Always shown on lock screen & shade (silent, selectable)"
    override val statusEnable = "Enable persistent notification"
    override val statusSrcCourse = "Next/current class"
    override val statusSrcPlan = "Ongoing / next plan"
    override val statusSrcAgenda = "Next event"
    override val statusSrcAi = "AI quick-add entry"
    override val statusHint = "Multi-select; shown silently on lock screen"
    override val statusChannelName = "Status"
    override val statusChannelDesc = "Always-on status of classes & events"
    override fun statusInClassFmt(name: String, end: String) = "In class: $name · until $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "Next class: $name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "Ongoing plan: $name"
    override fun statusNextPlanFmt(name: String, time: String) = "Next plan: $name · $time"
    override fun statusNextAgendaFmt(name: String, time: String) = "Next event: $name · $time"
    override val statusAiEntryTitle = "AI Assistant"
    override val statusAiEntryHint = "Tap to type · add / update / delete events"
    override val notifOk = "Notifications: enabled"
    override val notifNo = "Notifications: disabled"
    override val enableNotif = "Enable"

    override val secData = "Data & images"
    override val secDataSub = "Schedule is cached locally after sync"
    override val autoRefresh = "Auto-sync"
    override val autoCheckUpdate = "Auto-check for updates"
    override val fieldRepeatUntil = "Repeat until"
    override val repeatUntilNone = "No end"
    override val tabSchool = "School"
    override val comingSoon = "Coming soon"
    override val btnSyncNow = "Sync now"
    override val btnClearCache = "Clear cache"
    override val btnSaveDayImg = "Save day image"
    override val btnSaveWeekImg = "Save week image"
    override val btnSaveMonthImg = "Save month image"
    override val settingsTabFeature = "Features & permissions"
    override val settingsTabCustom = "Custom"
    override val settingsTabAccount = "School & account"
    override val settingsTabAccountSub = "Pick a school and save your sign-in"
    override val settingsTabFeatureSub =
        "Permissions, reminders, AI, floating ball, persistent notification, widgets"
    override val settingsTabCustomSub = "Timeline range and widget refresh rate"
    override val secPermissions = "Permissions"
    override val secPermissionsSub =
        "Grant on demand; the related feature stays unavailable without it (status is shown here)"
    override val permGranted = "Granted"
    override val permNotGranted = "Not granted"
    override val permRequest = "Request"
    override val permNetworkName = "Network access"
    override val permNetworkWhy = "Sync the timetable from the school system (granted at install)"
    override val permNotifName = "Notifications"
    override val permNotifWhy = "Class reminders, persistent notification, test notification"
    override val permOverlayName = "Overlay (display over other apps)"
    override val permOverlayWhy = "Show the AI floating ball over other apps"
    override val permExactName = "Exact alarms"
    override val permExactWhy =
        "Reminders fire on time; without it they can be delayed up to 10 minutes"
    override val permBatteryName = "Ignore battery optimizations"
    override val permBatteryWhy = "More reliable background reminders"
    override val permStorageName = "Storage (Android 8.0–9)"
    override val permStorageWhy = "Save schedule images to the system gallery"
    override val permInstallName = "Install unknown apps"
    override val permInstallWhy =
        "Needed by Settings → About → Check for updates → Download & install (other features work without it)"
    override fun permMissingCount(n: Int) = "$n not granted"
    override val imgDirNote = "Images are saved to Pictures/K日程 in your gallery."

    override val secLang = "Language"
    override val secLangSub = "简体中文 / English / Français (system default)"
    override val langSystem = "System default"

    override val secDev = "Developer tools"
    override val secDevSub = "Logs & diagnostics"
    override val openDevTools = "Open developer tools"
    override val secDevBrowser = "Built-in browser"
    override val secDevBrowserSub =
        "A plain browser (no script injection, no auto-navigation) with history, for observing page flows manually"
    override val btnOpenDevBrowser = "Open built-in browser"
    override val devBrowserTitle = "Built-in browser"
    override val devBrowserAddress = "Address"
    override val devBrowserGo = "Go"
    override val devBrowserForward = "Forward"
    override val devBrowserRefresh = "Refresh"
    override val devBrowserHistory = "History"
    override val devBrowserClear = "Clear"
    override val devBrowserEmpty = "No history yet"
    override val devBrowserClose = "Close"
    override val secNotifTest = "Notification test"
    override val secNotifTestSub = "Verify the notification & reminder pipeline"
    override val sendTestNotifBtn = "Send test notification now"
    override val sendTestReminderBtn = "Send test reminder in ~15 s"
    override val testNotifSent = "Test notification sent (check the notification shade if notifications are enabled)"
    override val testReminderScheduled = "Test reminder scheduled in ~15 s (may be delayed without exact-alarm permission)"
    override val secReminderTools = "Reminder scheduling tools"
    override fun reminderCountLabel(n: Int) = "Scheduled reminders: $n"
    override val rescheduleBtn = "Reschedule reminders"
    override val cancelAlarmsBtn = "Cancel all reminders"
    override val rescheduled = "Reminders rescheduled"
    override val alarmsCancelled = "All reminders cancelled"

    override val secReset = "Reset"
    override val secResetSub = "Clear data or restore defaults"
    override val resetClearData = "Clear events & plans"
    override val resetClearDataDesc = "Delete all local events and plans; schedule cache and account are kept."
    override val resetClearCache = "Clear cache & logs"
    override val resetClearCacheDesc = "Delete the schedule cache and runtime logs."
    override val resetLogoutDesc = "Clears the saved student ID & password, the web session and the built-in browser state; schedule cache and events are kept."
    override val resetAll = "Reset app"
    override val resetAllDesc = "Clears account, web session, built-in browser cache & local storage, schedule cache, events & plans, reminders, logs and language settings — equivalent to a freshly installed state."
    override val askResetTitle = "Reset the app?"
    override val askResetBody = "All data and settings will be cleared."
    override val askClearDataTitle = "Clear all events and plans?"
    override val askClearDataBody = "All local events and plans will be deleted. This cannot be undone."
    override val askClearCacheTitle = "Clear cache and logs?"
    override val askClearCacheBody = "The schedule cache and runtime logs will be deleted."

    override val secAbout = "About"
    override val settingsTabAboutFeedback = "About & feedback"
    override val aboutSub = "K Agenda ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "Local-first timetable & agenda app: your schedule is fetched from the school's academic system and cached on-device. " +
            "Day/week/month views (swipe to switch), local events & plans, class reminders, home-screen widgets, " +
            "persistent status notification, and a DeepSeek AI assistant (add/update/delete events; toolbar button or floating ball over other apps). " +
            "No personal data is uploaded except to the AI endpoint you configure."
    override val aboutTip = "Note: if syncing fails while the website works, the school pages may have changed. BUAA users can share the runtime log from Developer tools; other schools can update the adapter code in Settings → School → Add school."
    override val aboutPeriods = "Period count and times are fully customizable: 14 by default (4 morning + 5 afternoon + 5 evening). Add or remove periods and edit their times in Settings → Custom → Class times. When class info changes before the academic system catches up, use \u201cEdit course\u201d in the course details (this occurrence or all occurrences); Settings can restore them or clear and re-sync."

    override val updateCheck = "Check for updates"
    override fun updateCurrent(name: String) = "Current version: $name"
    override val updateChecking = "Checking for updates…"
    override val updateLatest = "You're on the latest version"
    override fun updateAvailable(name: String, size: String) = "New version $name ($size)"
    override val updateDownloadAndInstall = "Download & install"
    override val updateInstall = "Install"
    override fun updateDownloading(percent: Int) = "Downloading… $percent%"
    override fun updateDownloaded(size: String) = "Update downloaded ($size) — tap to install"
    override fun updateStartDownload(size: String) = "Downloading update package ($size)"
    override fun updateFailed(msg: String) = "Update failed: $msg"
    override val updateHintAvailable = "(Update!)"
    override fun updateHintDownloading(percent: Int) = "(Downloading $percent%)"
    override val updateHintInstall = "(Tap to install)"
    override val updateSizeUnknown = "unknown size"
    override val updatePermissionTitle = "\"Install unknown apps\" permission needed"
    override val updatePermissionBody =
        "To install the new K Agenda, allow this app to install unknown apps in system settings, then come back and retry."
    override val updateOpenSettings = "Open settings"
    override val updatePackageInvalid = "Update package unavailable — please download again"

    override val logTitle = "Runtime logs"
    override val logDesc = "Logs record each sync stage, page state and errors (never passwords). Share them with the developer for diagnosis."
    override val viewShareLog = "View / share logs"
    override val logFilterAll = "All"
    override val logFilterWarn = "Warn+"
    override val logFilterError = "Errors"
    override val clearLogBtn = "Clear logs"
    override val readingLog = "Reading logs…"
    override val emptyLog = "(No logs)"
    override val logShareSubject = "K Agenda logs"
    override val shareChooseFormat = "Share logs"
    override val shareFormatHint = "A log file (English name + timestamp) will be generated and shared. Choose the format:"
    override val shareAsTxt = "TXT text"
    override val shareAsMd = "Markdown"
    override val logMdTime = "Generated"
    override val logMdVersion = "App version"
    override val logMdDevice = "Device"
    override val diagTitle = "Diagnostics"
    override val diagVersion = "App version"
    override val diagSync = "Sync status"
    override val diagLastSync = "Last sync"
    override val diagNone = "None"

    override val msgNeedStudentId = "Enter your student ID"
    override val msgAccountSaved = "Account saved, syncing…"
    override val msgAccountSavedKeepPwd = "Student ID saved (no new password entered: syncing with the saved password, not verified)"
    override val msgLoggedOut = "Signed out: account and web session cleared"
    override val msgCredDeleted = "Saved credentials deleted (web session and cache kept)"
    override val msgResetDone = "App reset: please sign in again"
    override val msgEventsCleared = "Events and plans cleared"
    override val msgCacheLogsCleared = "Cache and logs cleared"
    override val msgCacheCleared = "Schedule cache cleared"
    override val msgReminderOn = "Class reminders enabled"
    override val msgReminderOff = "Class reminders disabled"
    override val msgNoScheduleData = "No schedule data yet — please sync first"
    override val msgSaveFailed = "Save failed, please retry"
    override val msgStorageNeedPerm =
        "Storage permission was not granted — the image could not be saved to the gallery " +
            "(needed on Android 8.0–9)"
    override val msgSavedDayImage = "Day schedule saved to gallery (Pictures/K日程)"
    override val msgSavedWeekImage = "Week schedule saved to gallery (Pictures/K日程)"
    override val msgSavedMonthImage = "Month schedule saved to gallery (Pictures/K日程)"

    override val channelName = "Class reminders"
    override val channelDesc = "Reminder notifications before classes"
    override fun notifClassSoon(title: String) = "Class soon: ${title}"
    override fun notifAgendaSoon(title: String) = "Starting soon: ${title}"
    override val syncTimeLabel = "Synced: "
    override val exportTimeLabel = "Exported: "

    override val backToToday = "Back to today"
    override val backToThisWeek = "Back to this week"

    override val secPeriodTimes = "Class times"
    override val secPeriodTimesSub = "Customize the start/end time of every period (14 periods by default)"
    override val btnEditPeriodTimes = "Customize class times"
    override val periodTimesTitle = "Class times"
    override val periodTimesHint = "Add or remove periods (1–24); enter times as 24-hour HH:mm (also 800 / 0800 / 8.00). Day/week views, widgets and the persistent notification update immediately."
    override val periodTimesRestoreDefault = "Restore defaults"
    override val periodTimesRowFmt = "Period %d"
    override fun periodTimesCount(n: Int) = "$n period(s) now"
    override fun periodTimesCountWarning(maxUsed: Int, configured: Int) =
        "Note: the timetable uses periods up to $maxUsed, but only $configured are configured here — extra courses will not be shown in the week view."
    override val periodTimesAddOne = "+ Add a period"
    override val periodTimesRemoveOne = "Remove this period"
    override val msgPeriodTimesSaved = "Class times updated"
    override val msgPeriodTimesInvalid = "Invalid time format — please fill every period as HH:mm"
    override val periodTimesDefaultTag = "Default schedule"
    override val periodTimesCustomTag = "Custom schedule"

    override val secLockScreen = "Show on lock screen"
    override val secLockScreenSub = "When off, the persistent notification stays off the lock screen"
    override val btnOpenNotifSettings = "Open system notification settings"
    override val lockScreenHint = "Notification missing on the lock screen? Some systems (MIUI/HyperOS, EMUI) hide silent notifications by default — enable “Lock screen notifications” for this app in the system settings."

    override val secUiStyle = "Appearance"
    override val secUiStyleSub = "Choose the overall visual style (default keeps the current look)"
    override val uiStyleDefault = "Default (Minimal)"
    override val uiStyleDefaultSub = "The current opaque card style"
    override val uiStyleGlass = "Liquid Glass"
    override val uiStyleGlassSub = "Gradient background + translucent frosted cards with softer corners"

    override val secDocs = "Document import"
    override val secDocsSub = "Read Word / Excel / PPT / PDF / text and detect timetables, events and plans"
    override val docTitle = "Document import"
    override val docIntro = "Pick a document; the text is extracted on this device and timetables, events and plans are detected automatically (nothing is uploaded)."
    override val docOpenHint = "Opening a file with \"Open with K日程\" from another app leads here as well."
    override val docPick = "Choose document"
    override val docReading = "Reading document…"
    override fun docReadFail(msg: String) = "Read failed: $msg"
    override fun docUnsupportedExt(ext: String) = ".$ext is not supported yet (re-save as docx / xlsx / pdf / txt)"
    override val docNoText = "No text could be extracted (scanned or image-only documents need manual pasting)"
    override val docTextLabel = "Document text (editable, then detect again)"
    override fun docDetected(events: Int, courses: Int) = "Detected: $events event(s)/plan(s), $courses course(s)"
    override val docImportAgenda = "Import as events"
    override val docImportPlan = "Import as plans"
    override val docImportCourses = "Import as timetable"
    override fun docAnchorLabel(date: String) = "Monday of teaching week 1: $date"
    override val docAnchorHint = "Course weeks are calculated from this date; adjust by weeks if needed"
    override fun docImportedList(n: Int) = "Imported courses ($n)"
    override val docClearImported = "Clear imported courses"
    override val docExport = "Export as document"
    override val docExportHint = "Export the current timetable and events as CSV / Markdown to a location you choose"
    override val docExportCsv = "Export CSV"
    override val docExportMd = "Export Markdown"
    override val docExported = "Exported to the selected location"
    override val docExportFailed = "Export failed"
    override fun msgImportedCourses(n: Int) = "$n course(s) imported"
    override val msgImportedCoursesCleared = "Imported courses cleared"
    override val msgImportNoCourses = "No course detected — make sure the document contains weekday and period info"
    override val msgImportNoEvents = "No event or plan detected"
    override val docLocalOnly = "Everything happens on this device; nothing is sent online"

    override val aiOfflineParse = "Offline detect"
    override fun aiOfflineDone(n: Int) = "Detected $n item(s) offline (no AI used)"

    // ---------------- Course edits ----------------
    override val tagEdited = "Edited"
    override val btnEditCourse = "Edit course"
    override val courseEditTitle = "Edit course"
    override val courseEditIntro =
        "Use this when the class info already changed but the academic system hasn't caught up. Edits stay on this device and never touch the academic system."
    override val editScopeLabel = "Apply to"
    override val editScopeOne = "Only this occurrence"
    override val editScopeOneHint = "Change just this block (weekday and periods can be adjusted too)"
    override val editScopeAll = "All occurrences of this course"
    override val editScopeAllHint = "Every weekday/period block of this course gets the same change"
    override val fieldCourseName = "Course name"
    override val editWeeksHint = "e.g. 2-17; leave empty for every week"
    override val editPeriodStart = "From period"
    override val editPeriodEnd = "To period"
    override val editNoChange = "No change detected"
    override val editSavedNote = "Edited on this device, not synced to the academic system yet"
    override val msgCourseEditSaved = "Course edit saved and marked as edited"
    override val msgImportedCourseEdited = "Imported course updated"
    override val msgCourseEditsRestored = "All course edits restored"
    override val msgCourseEditsClearedSyncing = "Course edits cleared, syncing with the academic system…"
    override val msgEditFollowed = "Now following the academic system"
    override val msgEditKept = "Your edit was kept"
    override fun msgEditConflicts(n: Int) = "$n course(s) differ from the academic system — please review"

    override val secCourseEdits = "Course edits"
    override val secCourseEditsSub = "Local fixes while the academic system lags behind"
    override fun courseEditsCount(n: Int) = "$n edited"
    override val courseEditsNone = "No course edits yet"
    override val courseEditsIntro =
        "Tap a course card in the timetable to edit its info. Once the academic system matches your edit, the \u201cEdited\u201d mark disappears automatically."
    override val btnRestoreCourseEdits = "Restore course edits only"
    override val btnSyncClearCourseEdits = "Sync with the academic system and clear all course edits"
    override val courseEditsHint =
        "\u201cRestore only\u201d puts the timetable back to the academic version (offline). \u201cSync and clear\u201d removes every edit and fetches the timetable again."
    override val askRestoreEditsTitle = "Restore all course edits?"
    override val askRestoreEditsBody =
        "Every course returns to the version saved by the academic system and the \u201cEdited\u201d marks disappear."
    override val askSyncClearEditsTitle = "Sync and clear course edits?"
    override val askSyncClearEditsBody =
        "All local course edits are removed and the timetable is fetched again; the academic data takes effect."

    override val editConflictTitle = "Course info differs from the academic system"
    override val editConflictIntro =
        "The academic system updated these courses, and the new data differs from your edit. Follow the academic system instead?"
    override val editConflictLatest = "Academic system now"
    override val editConflictYours = "Your edit"
    override val editConflictMissing = "This course is no longer in the academic system"
    override val btnFollowAcademic = "Follow academic"
    override val btnKeepMine = "Keep my edit"
    override val btnFollowAll = "Follow academic for all"
    override val btnKeepAll = "Keep all my edits"
    override val editConflictLater = "Later"
    // ---------------- Manual timetable edits: delete / add / move ----------------
    override val editActionModify = "Edit info"
    override val editActionDelete = "Delete course"
    override val editActionMove = "Reschedule"
    override val editDeleteIntro =
        "The course is hidden in the selected weeks (local only; the academic system is untouched)."
    override val editDeleteWeeksHint = "Select all = hide the whole course"
    override fun editMoveIntro(n: Int) =
        "Move this day's courses (all or one) to another date (local only). This day has $n course(s)."
    override val editMoveWholeDay = "Whole day together"
    override fun editMoveWholeDayHint(n: Int) = "All $n course(s) of this day move to the target date"
    override val editMoveSingle = "Several courses"
    override val editMoveSingleHint = "Tick the courses to move (multi-select); the others stay"
    override val editMoveTarget = "Target date"
    override val editMovePickDate = "Pick a date"
    override val weeksAll = "All"
    override val weeksNone = "Clear"
    override val courseAddTitle = "Add course"
    override val courseAddIntro =
        "Add a course missing from the academic system (local only; nothing is uploaded)."
    override val btnAddCourse = "Add course"
    override val btnCopyCourse = "Copy course"
    override val aboutOfficialSite = "Official site / downloads"
    override val aboutGithubRepo = "GitHub repository"

    override val secFeedback = "Feedback"
    override val secFeedbackSub = "Bugs, ideas or school support"
    override val feedbackIntro =
        "Feedback is sent to the developer's site (20071009.xyz). Never include credentials."
    override val feedbackTopic = "Topic"
    override val topicBug = "Bug report"
    override val topicFeature = "Feature request"
    override val topicSchool = "School support"
    override val topicOther = "Other"
    override val feedbackPlaceholder = "Describe the problem or the feature you want..."
    override val feedbackContact = "Contact (optional)"
    override val btnFeedbackSend = "Send"
    override val msgFeedbackSent = "Feedback sent, thank you!"
    override val msgFeedbackFailed = "Send failed; check your network and retry"
    override val feedbackEmpty = "Please write something first"
    override val feedbackIncludeNote =
        "Attached automatically: app version, Android version, current school (no student ID, password or timetable content)"
    override val btnRescheduleDate = "Move to..."
    override val longPressHint = "Long-press a date to move its courses"
    override val rescheduleTitle = "Move to..."
    override fun rescheduleIntro(month: Int, day: Int, n: Int) =
        "$month/$day has $n course(s). Choose what to move and the target date."
    override val rescheduleSingleHint = "Move a single course (pick below)"
    override val msgCourseDeleted = "Deleted (local only)"
    override val msgCourseAdded = "Course added (local only)"
    override fun msgCourseMoved(n: Int, month: Int, day: Int) = "Moved $n course(s) to $month/$day"
    override val msgMoveSameDay = "Target date is the same as the source"
    override val msgMoveNoWeek = "Cannot determine the teaching week; sync the timetable first"

    override val secHolidayCourses = "Holiday timetable"
    override val secHolidayCoursesSub = "Off by default (holiday classes hidden)"
    override val holidayCoursesSwitch = "Show classes on public holidays"
    override val holidayCoursesNote =
        "When on, classes on public holidays are shown and reminders fire as usual (handy for rescheduling)."

    override val secBackup = "Backup & restore"
    override val secBackupSub = "Export before switching devices or reinstalling"
    override val backupIntro =
        "Export a single backup file (timetable cache, agenda and plans, course edits, all settings) and import it after reinstalling or switching devices."
    override val backupIncludes =
        "The backup contains your student ID, timetable, agenda and settings. It excludes the password and API key (they cannot be decrypted on another device)."
    override val btnBackupExport = "Export backup"
    override val btnBackupImport = "Import backup"
    override val backupAskImportTitle = "Import backup?"
    override val backupAskImportBody =
        "The backup will overwrite the current timetable, agenda and settings (password and API key are kept)."
    override val backupFileName = "KAgenda_backup"
    override val backupExportTitle = "Export backup to"
    override val backupExportDefault = "Default location (recommended)"
    override val backupExportCustom = "Choose another location..."
    override fun msgBackupExportedDefault(dir: String, name: String) = "Backup saved to $dir/$name"
    override val backupImportTitle = "Import backup"
    override fun backupImportFound(n: Int, dir: String) = "Found $n backup(s) in $dir:"
    override val backupImportPickOther = "Choose another file..."
    override fun backupDefaultDirNote(dir: String) =
        "Default location: $dir (kept after reinstalling; checked first when importing)"
    override fun msgBackupExported(size: String) = "Backup exported ($size)"
    override val msgBackupExportFailed = "Backup export failed"
    override fun msgBackupImported(agenda: Int, edits: Int, courses: Int) =
        "Backup imported: $agenda agenda/plan item(s), $edits course edit(s), $courses imported course(s)"
    override val msgBackupInvalid = "Not a KAgenda backup file"
    override fun msgBackupNewer(version: Int) = "Backup format v$version is newer than this app; update the app first"
    override fun msgBackupFailed(detail: String) = "Import failed: $detail"
}

// ==================================================================== Français

object FrStrings : AppStrings {
    override val appTitle = "K Agenda"
    override val brand = "K Agenda"
    override val save = "Enregistrer"
    override val cancel = "Annuler"
    override val delete = "Supprimer"
    override val confirm = "OK"
    override val back = "Retour"
    override val refresh = "Actualiser"
    override val share = "Partager"

    override val syncSyncedAt = "Synchronisé · "
    override val syncNotLoggedIn = "Non connecté"
    override val syncRunning = "Synchronisation…"
    override val syncHintNeedLogin = "Pas encore connecté. Saisissez votre identifiant et mot de passe dans Réglages, puis synchronisez."
    override val syncHintRunning = "Récupération de l'emploi du temps…"
    override val noScheduleTitle = "Aucune donnée d'emploi du temps"
    override val noScheduleHintDay = "Connectez-vous d'abord dans Réglages (identifiant + mot de passe), puis appuyez sur Actualiser en haut à droite."
    override val noScheduleHintWeek = "Connectez-vous et synchronisez dans Réglages pour voir la semaine."
    override val noCoursesToday = "Aucun cours ce jour-là"
    override val noCoursesTodayHint = "Changez de date ou actualisez pour synchroniser les dernières données."
    override val dayNoCoursesImage = "Aucun cours ce jour-là 🎉"

    override val tabSchedule = "Agenda"
    override val tabPlan = "Plans"
    override val tabOngoing = "En cours"
    override val tabSettings = "Réglages"
    override val viewDay = "Jour"
    override val viewWeek = "Semaine"
    override val viewMonth = "Mois"
    override val timetableMode = "Mode emploi du temps"

    override val prevWeek = "Semaine précédente"
    override val nextWeek = "Semaine suivante"
    override val prevMonth = "Mois précédent"
    override val nextMonth = "Mois suivant"
    override val backToThisMonth = "Mois actuel"
    override val today = "Aujourd'hui"
    override val saveDaySchedule = "Enregistrer le jour"
    override val saveWeekSchedule = "Enregistrer la semaine"
    override val saveMonthSchedule = "Enregistrer le mois"

    override fun weekNo(n: Int) = "Semaine ${n}"
    override fun weekdayShort(i: Int) =
        arrayOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")[(i - 1).coerceIn(0, 6)]
    override fun weekdayNarrow(i: Int) =
        arrayOf("L", "M", "M", "J", "V", "S", "D")[(i - 1).coerceIn(0, 6)]
    override fun monthTitle(year: Int, month: Int) =
        arrayOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet",
            "Août", "Septembre", "Octobre", "Novembre", "Décembre")[(month - 1).coerceIn(0, 11)] + " " + year
    override fun dayImageTitle(month: Int, day: Int, dow: Int) =
        "${day} " + arrayOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.",
            "août", "sept.", "oct.", "nov.", "déc.")[(month - 1).coerceIn(0, 11)] +
            ", " + weekdayShort(dow)

    override fun monthImageTitle(year: Int, month: Int) = "Emploi du mois · ${monthTitle(year, month)}"
    override val weekImageTitle = "Emploi du temps hebdo"

    override fun periodNo(n: Int) = "Période ${n}"
    override fun periodNoShort(n: Int) = "#${n}"
    override fun periodsRange(a: Int, b: Int) = "Périodes ${a}-${b}"
    override fun courseCount(n: Int) = "${n} cours"
    override fun eventCount(n: Int) = "${n} événements"
    override fun weeksValue(raw: String) = "Semaines ${raw}"
    override val detailTime = "Horaire"
    override val detailWeekday = "Jour"
    override val detailTeacher = "Enseignant"
    override val detailRoom = "Salle"
    override val detailWeeks = "Semaines"
    override val detailCode = "Code du cours"
    override val tagOngoing = "En cours"

    override val myAgenda = "Mes événements"
    override val myAgendaWeek = "Mes événements (cette semaine)"
    override val addAgendaBtn = "＋ Ajouter un événement"
    override val emptyAgendaHint = "Aucun événement. Appuyez sur « ＋ Ajouter un événement »."
    override val agendaSaved = "Événement enregistré"
    override val agendaDeleted = "Événement supprimé"
    override val myPlans = "Mes plans"
    override val myPlansWeek = "Mes plans (cette semaine)"
    override val addPlanBtn = "＋ Ajouter un plan"
    override val emptyPlansHint = "Aucun plan. Appuyez sur « ＋ Ajouter un plan »."
    override val planSaved = "Plan enregistré"
    override val planDeleted = "Plan supprimé"
    override val mixedTitle = "Aujourd'hui (cours + événements)"
    override val tagLong = "Événement long"
    override val tagAgenda = "Événement"
    override val tagPlan = "Plan"

    override val editorAddAgenda = "Ajouter un événement"
    override val editorEditAgenda = "Modifier l'événement"
    override val editorAddPlan = "Ajouter un plan"
    override val editorEditPlan = "Modifier le plan"
    override val fieldTitle = "Titre"
    override val labelColor = "Couleur"
    override val colorAuto = "Auto"
    override val segShort = "Un jour"
    override val segLong = "Longue durée"
    override val segShortPlan = "Plan court"
    override val segLongPlan = "Plan long"
    override val secTimelineRange = "Plage de la frise"
    override val secTimelineRangeSub = "Heures de début/fin de la frise hebdomadaire (mode emploi du temps désactivé). Une fin antérieure au début passe au lendemain (par défaut 06:00 – 02:00 le lendemain)"
    override val secWeekDays = "Jours de la vue semaine"
    override val secWeekDaysSub = "Nombre de jours affichés par défaut dans la vue semaine ; partagé par Emploi du temps et Plans (pincer pour zoomer)"
    override val weekDaysFive = "5 jours (lun.–ven.)"
    override val weekDaysSeven = "7 jours (lun.–dim.)"
    override val labelNextDay = "lendemain"
    override val secWidget = "Widget"
    override val secWidgetSub = "Épingler un widget d'emploi du temps à l'accueil"
    override val secWidgetRefresh = "Fréquence de rafraîchissement"
    override val secWidgetRefreshSub =
        "Plus le prochain élément approche, plus l'actualisation est fréquente (1 / 5 / 60 min par défaut)"
    override val widgetRefreshCustom = "Fréquence personnalisée"
    override val widgetRefreshCustomOn = "Perso."
    override val widgetRefreshDefaultTag = "Défaut"
    override val widgetRefreshDefaultNote =
        "Fréquence par défaut : toutes les 1 min à moins d'une heure, toutes les 5 min à moins de 3 h, toutes les 60 min sinon. Activez l'interrupteur ci-dessus pour personnaliser."
    override val widgetRefreshNear = "À moins d'une heure"
    override val widgetRefreshSoon = "À moins de 3 h"
    override val widgetRefreshFar = "Plus loin / rien de prévu"
    override val widgetPinHint = "Touchez une taille pour l'épingler ; si votre lanceur ne le permet pas, appuyez longuement sur l'écran d'accueil et ajoutez K Agenda depuis la liste des widgets"
    override val widgetPinUnsupported = "L'épinglage depuis l'appli n'est pas pris en charge — appuyez longuement sur l'accueil et ajoutez K Agenda depuis la liste des widgets"
    override val fieldDate = "Date"
    override val fieldStartTime = "Début"
    override val fieldEndTime = "Fin"
    override val fieldStartDate = "Date de début"
    override val fieldEndDate = "Date de fin"
    override val unsetTime = "Non défini"
    override val fieldLocation = "Lieu (facultatif)"
    override val fieldNote = "Remarque (facultatif)"
    override val errTitle = "Saisissez un titre"
    override val errEndBeforeStart = "La fin ne peut pas précéder le début"
    override val askDeleteAgenda = "Supprimer l'événement ?"
    override val askDeletePlan = "Supprimer le plan ?"
    override val pickerTimeTitle = "Choisir l'heure"
    override val repeatSection = "Répétition"
    override val repeatNone = "Aucune"
    override val repeatDailyMode = "Tous les N jours"
    override val repeatWeeklyMode = "Chaque semaine"
    override val repeatBiweeklyMode = "Une semaine sur deux"
    override val repeatMonthlyMode = "Chaque mois"
    override val repeatFieldHint = "ex. mar./jeu./sam. chaque semaine, un mardi sur deux, tous les 3 jours (vide = inchangé)"
    override val errRepeatNoDay = "Aucun jour sélectionné : choisissez au moins un jour de la semaine"
    override val repeatNeedDayHint = "Choisissez au moins un jour (sinon impossible d'enregistrer)"
    override fun repeatEveryNDays(n: Int) = if (n <= 1) "Tous les jours" else "Tous les $n jours"
    override fun repeatLabel(rule: String) = when {
        rule.isBlank() -> ""
        rule == "monthly" -> "Chaque mois"
        rule.startsWith("daily:") -> repeatEveryNDays(rule.removePrefix("daily:").toIntOrNull() ?: 1)
        rule.startsWith("weekly:") -> {
            val days = ruleDayList(rule.removePrefix("weekly:"))
            if (days.size == 7) "Tous les jours" else "Chaque semaine · " + dayNamesOf(days)
        }
        rule.startsWith("biweekly:") -> "Une semaine sur deux · " + dayNamesOf(ruleDayList(rule.removePrefix("biweekly:")))
        else -> ""
    }

    private fun ruleDayList(body: String) =
        body.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }

    private fun dayNamesOf(days: List<Int>) = days.joinToString("/") { weekdayShort(it) }

    override fun deleteBody(name: String) = "« ${name} » sera supprimé. Action irréversible."
    override fun typeLabel(key: String) = when (key) {
        "interview" -> "Entretien"; "contest" -> "Compétition"; "lecture" -> "Conférence"
        "exam" -> "Examen"; "meeting" -> "Réunion"; "other" -> "Autre"
        else -> ""
    }

    override val pasteLabel = "Collez une annonce — heure / lieu / type détectés automatiquement"
    override val pastePlaceholder = "ex. 18 sept. 15h50 conférence au C1-2003"
    override val pasteBtn = "Analyser et remplir"
    override val parseOk = "Reconnu — veuillez vérifier"
    override val parsePartial = "Partiellement reconnu — complétez les champs manquants"
    override val parseFail = "Non reconnu — saisissez manuellement"

    override fun ongoingHeader(n: Int) = "Événements longs / plans en cours (${n})"
    override val ongoingEmptyTitle = "Aucun événement long ni plan en cours"
    override val ongoingEmptyHint =
        "Les événements longs ou plans couvrent plusieurs jours (ex. inscription 9/10 10:00 – 10/30 22:00). " +
            "Créez-en un depuis Agenda ou Plans en choisissant « Longue durée » ou « Plan long »."
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "Reste ${days} j ${hours} h"
        hours > 0 -> "Reste ${hours} h ${mins} min"
        else -> "Reste ${mins} min"
    }

    override val secAccount = "Compte"
    override fun secAccountSubOf(schoolId: String, schoolName: String): String {
        // 学校专属称呼（若有）统一放在 SchoolTexts，本处不含任何学校 id 分支
        val sys = SchoolTexts.accountSystemName(schoolId, AppLang.FR)
        return if (sys != null) "Connexion à $sys" else "Connexion au système académique de $schoolName"
    }
    override val labelStudentId = "Identifiant étudiant"
    override val secProfile = "Profil d'identité"
    override val secProfileSub =
        "Renseignez votre université/filière/année/classe : si une annonce liste plusieurs classes ou créneaux, l'IA n'ajoute que celui qui vous correspond"
    override val labelCollege = "Université"
    override val labelMajor = "Filière"
    override val labelGrade = "Année"
    override val labelClazz = "Classe"
    override val btnSaveProfile = "Enregistrer le profil"
    override val aiDoneTitle = "Analyse IA terminée"
    override fun aiDoneBody(n: Int) = "$n élément(s) trouvé(s) — appuyez pour voir"
    override val aiFailedTitle = "Échec de l'analyse IA"
    override val channelAiName = "Résultats IA"
    override val channelAiDesc = "Alerte flottante à la fin de l'analyse (désactivable séparément)"
    override val aiBadgeDesc = "Résultat IA non consulté"
    override val labelPassword = "Mot de passe"
    override val labelPasswordKeep = "Mot de passe (enregistré — laisser vide pour conserver)"
    override val btnSaveAndSync = "Enregistrer et synchroniser"
    override val btnLogout = "Se déconnecter"
    override val btnClearCred = "Supprimer les identifiants"
    override fun statusSaved(id: String) = "Connecté : compte enregistré (${id})"
    override val statusSession = "Connecté : session active (sans mot de passe)"
    override val statusNone = "Non connecté"
    override fun lastSyncAt(time: String) = "Dernière synchro : ${time}"
    override val neverSynced = "Jamais"
    override val accountFootnote =
        "Vos identifiants sont stockés uniquement sur cet appareil (chiffrés via Android Keystore) et servent à la connexion automatique. " +
            "« Supprimer les identifiants » n'efface que l'identifiant et le mot de passe locaux (session web et cache conservés) ; " +
            "« Se déconnecter » efface aussi la session web. En cas de captcha, réessayez plus tard."
    override val labelViaWebVpn = "Via WebVPN"
    override val labelViaWebVpnSub =
        "Coché : accéder au système académique via WebVPN. Décoché : itinéraire par défaut. Les deux itinéraires sont implémentés séparément et n'affectent pas l'autre."
    override val askClearCredTitle = "Supprimer les identifiants ?"
    override val askClearCredBody = "L'identifiant et le mot de passe locaux seront supprimés ; la session web et le cache sont conservés."

    override val secReminder = "Rappel de cours"
    override val secReminderSub = "Une notification avant chaque cours"
    override val reminderEnable = "Activer les rappels"
    override val remindAgendaLabel = "Rappeler aussi les événements/plans (avec une heure de début)"
    override val leadLabel = "Délai avant"
    override fun minutes(n: Int) = "${n} min"
    override val exactOk = "Alarmes exactes : disponibles"
    override val exactNo = "Alarmes exactes : non autorisées (retard possible de 10 min)"
    override val gotoGrant = "Autoriser"
    override val batteryOk = "Batterie : sans restriction"
    override val batteryNo = "Batterie : restreinte (rappels possibles en retard)"
    override val gotoExempt = "Autoriser"

    // École
    override val secSchool = "École"
    override val secSchoolSub = "Choisissez l'école de votre emploi du temps"
    override val schoolChange = "Changer"
    override fun msgSchoolSwitched(name: String) = "Passé à $name"

    // 学校适配器（添加学校）
    override val schoolAdd = "Ajouter une école"
    override val adapterName = "Nom de l'école"
    override val adapterSite = "Site web (optionnel)"
    override val adapterGen = "Générer le code (IA)"
    override val adapterCodeLabel = "Code d'adaptation (JSON)"
    override val adapterTemplate = "Copier le modèle"
    override val adapterCopied = "Modèle copié"
    override val adapterSave = "Enregistrer"
    override val adapterNeedName = "Saisissez le nom"
    override fun adapterInvalid(msg: String) = "Format invalide : $msg"
    override fun adapterAdded(name: String) = "École ajoutée : $name"
    override val adapterExtraLabel = "Indices supplémentaires (facultatif)"
    override val adapterExtraHint = "Collez l'URL du système académique, des notes d'API ou des extraits de page pour aider l'IA"
    override val adapterWarnTitle = "Fonction susceptibile de ne pas fonctionner"
    override val adapterWarnBody =
        "Les systèmes académiques diffèrent fortement : un adaptateur générique (y compris généré par l'IA) " +
            "ne fonctionne souvent pas tel quel — il faudra peut-être modifier les scripts et déboguer sur un appareil réel. " +
            "L'entrée reste disponible pour essayer, sans garantie de réussite."
    override val adapterStageRequest = "Demande d'adaptation à DeepSeek…"
    override val adapterStageParse = "Analyse du code généré…"
    override val adapterStageDone = "Adaptation générée — vérifiez puis enregistrez"
    override fun adapterWaiting(sec: Int) = " (en attente ${sec} s…)"

    // AI 助手（悬浮入口）
    override val qaTitle = "Assistant IA"
    override val qaHint = "Saisissez ou collez : ajouter des événements/plans, ou demander à l'IA de modifier/supprimer (ex. « déplacer la visite à demain après-midi »)"
    override val qaParse = "Analyse IA"
    override val qaConfirm = "Tout exécuter"
    override val qaNothing = "Rien à exécuter"
    override val qaStaleResult = "Texte modifié — résultat ignoré. Veuillez relancer l'analyse."
    override val qaReparseTitle = "Relancer l'analyse ?"
    override val qaReparseMsg = "Relancer l'analyse effacera l'aperçu actuel (y compris les modifications manuelles)."
    override val opDateInvalid = "Date invalide — utilisez le format 2026-09-20"
    override val discardTitle = "Abandonner les modifications non enregistrées ?"
    override val discardOk = "Abandonner"
    override val msgExporting = "Génération de l'image en cours…"
    override val qaPlanTag = "Plan"
    override val qaAgendaTag = "Événement"
    override fun qaAdded(n: Int) = "$n élément(s) ajouté(s)"
    override val opAdd = "Ajouter"
    override val opUpdate = "Modifier"
    override val opDelete = "Supprimer"
    override val opMatchTitle = "Titre cible"
    override val opMatchDate = "Date cible (optionnel, ex. 2026-09-20)"
    override val opNewTitle = "Nouveau titre (vide = inchangé)"
    override val opNewDate = "Nouvelle date (vide = inchangé)"
    override val opNewStart = "Nouveau début (vide = inchangé)"
    override val opNewEnd = "Nouvelle fin (vide = inchangé)"
    override val opNewLocation = "Nouveau lieu (vide = inchangé)"
    override fun qaOpsDone(added: Int, updated: Int, deleted: Int, unmatched: Int): String {
        val parts = buildList {
            if (added > 0) add("$added ajouté(s)")
            if (updated > 0) add("$updated modifié(s)")
            if (deleted > 0) add("$deleted supprimé(s)")
            if (unmatched > 0) add("$unmatched sans cible")
        }
        return parts.joinToString(" · ").ifBlank { "Aucune action" }
    }
    override val timeExact = "Heure précise"
    override val timeFuzzy = "Heure approximative"
    override val timeFuzzyHint = "Choisissez une période (凌晨 / 早晨 / 上午 / 下午 / 晚上 / 午夜) ; compte à rebours par jour"

    override val secOverlay = "Bulle flottante"
    override val secOverlaySub = "Afficher la bulle de l'assistant IA par-dessus n'importe quelle appli"
    override val overlayEnable = "Activer la bulle"
    override val overlayHint = "Relâchez la bulle pour la coller au bord de l'écran le plus proche"
    override val overlayNeedPerm = "Nécessite l'autorisation « Afficher par-dessus les autres applis »"
    override val overlayGrant = "Autoriser"
    override val qaOpenInApp = "Ouvrir dans l'appli"
    override val overlayNotifTitle = "Bulle IA"
    override val overlayNotifText = "Appuyez sur la bulle pour saisir et ajouter des événements"
    override val overlayNotifStop = "Désactiver la bulle"
    override val qaReParse = "Texte modifié — relancez l'analyse IA"

    // IA (DeepSeek)
    override val secAi = "Reconnaissance IA (DeepSeek)"
    override val secAiSub = "Avec une clé API, ajoutez, modifiez ou supprimez en langage naturel"
    override val aiKeyLabel = "Clé API DeepSeek"
    override val aiKeyKeepHint = "Enregistrée (laisser vide pour garder)"
    override val aiUseDevKey = "Utiliser la clé API du développeur"
    override val aiDevKeyInUse = "Clé API intégrée activée — l'IA est prête à l'emploi"
    override val aiDevModelFixed = "Fixé sur deepseek-flash avec la clé du développeur"
    override val aiModelLabel = "Modèle"
    override val aiSave = "Enregistrer"
    override val aiClear = "Effacer la clé"
    override val aiSavedToast = "Réglages IA enregistrés (clé chiffrée)"
    override val aiClearedToast = "Clé IA effacée"
    override val aiBtn = "IA"
    override val aiRunning = "Appel de l'IA…"
    override val aiOk = "Analyse IA terminée"
    override val aiNeedKey = "Configurez la clé API DeepSeek dans Réglages → IA"

    // Notification permanente
    override val secStatus = "Notification permanente"
    override val secStatusSub = "Toujours affichée (écran verrouillé inclus)"
    override val statusEnable = "Activer"
    override val statusSrcCourse = "Cours suivant/en cours"
    override val statusSrcPlan = "Plan en cours / suivant"
    override val statusSrcAgenda = "Prochain événement"
    override val statusSrcAi = "Entrée rapide IA"
    override val statusHint = "Multi-sélection ; silencieuse"
    override val statusChannelName = "Statut"
    override val statusChannelDesc = "Statut permanent des cours et événements"
    override fun statusInClassFmt(name: String, end: String) = "En cours : $name · jusqu'à $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "Cours suivant : $name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "Plan en cours : $name"
    override fun statusNextPlanFmt(name: String, time: String) = "Prochain plan : $name · $time"
    override fun statusNextAgendaFmt(name: String, time: String) = "Prochain événement : $name · $time"
    override val statusAiEntryTitle = "Assistant IA"
    override val statusAiEntryHint = "Appuyez pour saisir · ajouter / modifier / supprimer"
    override val notifOk = "Notifications : activées"
    override val notifNo = "Notifications : désactivées"
    override val enableNotif = "Activer"

    override val secData = "Données et images"
    override val secDataSub = "L'emploi du temps est mis en cache localement après synchro"
    override val autoRefresh = "Synchronisation automatique"
    override val autoCheckUpdate = "Vérification auto des mises à jour"
    override val fieldRepeatUntil = "Répéter jusqu'au"
    override val repeatUntilNone = "Sans fin"
    override val tabSchool = "École"
    override val comingSoon = "À venir"
    override val btnSyncNow = "Synchroniser"
    override val btnClearCache = "Vider le cache"
    override val btnSaveDayImg = "Image du jour"
    override val btnSaveWeekImg = "Image de la semaine"
    override val btnSaveMonthImg = "Image du mois"
    override val settingsTabFeature = "Fonctions et autorisations"
    override val settingsTabCustom = "Personnalisation"
    override val settingsTabAccount = "École et compte"
    override val settingsTabAccountSub = "Choisir l'école et enregistrer le compte"
    override val settingsTabFeatureSub =
        "Autorisations, rappels, IA, bulle, notification permanente, widgets"
    override val settingsTabCustomSub = "Plage de la frise et fréquence des widgets"
    override val secPermissions = "Autorisations"
    override val secPermissionsSub =
        "À accorder à la demande ; la fonction associée reste indisponible sinon"
    override val permGranted = "Accordé"
    override val permNotGranted = "Non accordé"
    override val permRequest = "Demander"
    override val permNetworkName = "Accès réseau"
    override val permNetworkWhy =
        "Synchroniser l'emploi du temps depuis le système scolaire (accordé à l'installation)"
    override val permNotifName = "Notifications"
    override val permNotifWhy = "Rappels de cours, notification permanente, notification de test"
    override val permOverlayName = "Superposition (par-dessus les autres applis)"
    override val permOverlayWhy = "Afficher la bulle IA par-dessus les autres applis"
    override val permExactName = "Alarmes exactes"
    override val permExactWhy =
        "Rappels à l'heure ; sinon ils peuvent être retardés de 10 minutes"
    override val permBatteryName = "Ignorer l'optimisation de batterie"
    override val permBatteryWhy = "Rappels en arrière-plan plus fiables"
    override val permStorageName = "Stockage (Android 8.0–9)"
    override val permStorageWhy = "Enregistrer les images dans la galerie système"
    override val permInstallName = "Installer des applis inconnues"
    override val permInstallWhy =
        "Nécessaire pour Réglages → À propos → Vérifier les mises à jour → Télécharger et installer (sans effet sur les autres fonctions)"
    override fun permMissingCount(n: Int) = "$n non accordées"
    override val imgDirNote = "Les images sont enregistrées dans Pictures/K日程."

    override val secLang = "Langue / Language"
    override val secLangSub = "简体中文 / English / Français (langue du système par défaut)"
    override val langSystem = "Langue du système"

    override val secDev = "Outils développeur"
    override val secDevSub = "Journaux et diagnostics"
    override val openDevTools = "Ouvrir les outils développeur"
    override val secDevBrowser = "Navigateur intégré"
    override val secDevBrowserSub =
        "Navigateur simple (sans injection de script ni navigation automatique) avec historique, pour observer les pages manuellement"
    override val btnOpenDevBrowser = "Ouvrir le navigateur intégré"
    override val devBrowserTitle = "Navigateur intégré"
    override val devBrowserAddress = "Adresse"
    override val devBrowserGo = "Aller"
    override val devBrowserForward = "Avancer"
    override val devBrowserRefresh = "Recharger"
    override val devBrowserHistory = "Historique"
    override val devBrowserClear = "Effacer"
    override val devBrowserEmpty = "Aucun historique"
    override val devBrowserClose = "Fermer"
    override val secNotifTest = "Test de notification"
    override val secNotifTestSub = "Vérifier le circuit notifications / rappels"
    override val sendTestNotifBtn = "Envoyer une notification de test"
    override val sendTestReminderBtn = "Rappel de test dans ~15 s"
    override val testNotifSent = "Notification de test envoyée (visible si les notifications sont activées)"
    override val testReminderScheduled = "Rappel de test prévu dans ~15 s (peut être retardé sans alarmes exactes)"
    override val secReminderTools = "Outils de planification des rappels"
    override fun reminderCountLabel(n: Int) = "Rappels planifiés : $n"
    override val rescheduleBtn = "Replanifier les rappels"
    override val cancelAlarmsBtn = "Annuler tous les rappels"
    override val rescheduled = "Rappels replanifiés"
    override val alarmsCancelled = "Tous les rappels annulés"

    override val secReset = "Réinitialisation"
    override val secResetSub = "Effacer des données ou restaurer les réglages"
    override val resetClearData = "Effacer événements et plans"
    override val resetClearDataDesc = "Supprime tous les événements et plans locaux ; cache et compte conservés."
    override val resetClearCache = "Vider cache et journaux"
    override val resetClearCacheDesc = "Supprime le cache de l'emploi du temps et les journaux."
    override val resetLogoutDesc = "Efface l'identifiant et le mot de passe enregistrés, la session web et l'état du navigateur intégré ; le cache et les événements sont conservés."
    override val resetAll = "Réinitialiser l'application"
    override val resetAllDesc = "Efface compte, session web, cache et stockage local du navigateur intégré, cache du planning, événements et plans, rappels, journaux et réglages de langue — équivaut à une installation neuve."
    override val askResetTitle = "Réinitialiser l'application ?"
    override val askResetBody = "Toutes les données et tous les réglages seront effacés."
    override val askClearDataTitle = "Effacer tous les événements et plans ?"
    override val askClearDataBody = "Action irréversible."
    override val askClearCacheTitle = "Vider le cache et les journaux ?"
    override val askClearCacheBody = "Le cache et les journaux seront supprimés."

    override val secAbout = "À propos"
    override val settingsTabAboutFeedback = "À propos & retours"
    override val aboutSub = "K Agenda ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "Application locale d'abord : l'emploi du temps est récupéré depuis le système académique de l'école et mis en cache sur l'appareil. " +
            "Vues jour/semaine/mois (balayage pour changer), événements et plans locaux, rappels de cours, widgets, " +
            "notification permanente et ajout rapide par IA DeepSeek (bouton ou bulle flottante au-dessus des autres applis). " +
            "Aucune donnée personnelle n'est envoyée, hormis vers l'API IA que vous configurez."
    override val aboutTip = "Remarque : si la synchro échoue alors que le site fonctionne, les pages de l'école ont peut-être changé. Les utilisateurs BUAA peuvent partager le journal via Outils développeur ; pour les autres écoles, mettez à jour le code d'adaptation dans Réglages → École → Ajouter une école."
    override val aboutPeriods = "Le nombre de périodes et leurs horaires sont entièrement personnalisables : 14 par défaut (4 le matin + 5 l'après-midi + 5 le soir). Ajoutez/supprimez des périodes dans Réglages → Personnalisation → Horaires des cours. Si les informations changent avant le système scolaire, utilisez « Modifier le cours » dans le détail du cours (cette occurrence ou toutes) ; les réglages permettent de restaurer ou d'effacer puis resynchroniser."

    override val updateCheck = "Vérifier les mises à jour"
    override fun updateCurrent(name: String) = "Version actuelle : $name"
    override val updateChecking = "Vérification des mises à jour…"
    override val updateLatest = "Vous avez la dernière version"
    override fun updateAvailable(name: String, size: String) = "Nouvelle version $name ($size)"
    override val updateDownloadAndInstall = "Télécharger et installer"
    override val updateInstall = "Installer"
    override fun updateDownloading(percent: Int) = "Téléchargement… $percent %"
    override fun updateDownloaded(size: String) = "Mise à jour téléchargée ($size) — appuyez pour installer"
    override fun updateStartDownload(size: String) = "Téléchargement du paquet de mise à jour ($size)"
    override fun updateFailed(msg: String) = "Échec de la mise à jour : $msg"
    override val updateHintAvailable = "(Mise à jour !)"
    override fun updateHintDownloading(percent: Int) = "(Téléchargement $percent %)"
    override val updateHintInstall = "(Appuyer pour installer)"
    override val updateSizeUnknown = "taille inconnue"
    override val updatePermissionTitle = "Autorisation « Installer des applis inconnues » requise"
    override val updatePermissionBody =
        "Pour installer la nouvelle version de K Agenda, autorisez cette application à installer des applis inconnues dans les réglages système, puis revenez réessayer."
    override val updateOpenSettings = "Ouvrir les réglages"
    override val updatePackageInvalid = "Paquet de mise à jour indisponible — veuillez retélécharger"

    override val logTitle = "Journaux d'exécution"
    override val logDesc = "Les journaux enregistrent chaque étape de synchro, l'état des pages et les erreurs (jamais les mots de passe)."
    override val viewShareLog = "Voir / partager les journaux"
    override val logFilterAll = "Tout"
    override val logFilterWarn = "Alertes+"
    override val logFilterError = "Erreurs"
    override val clearLogBtn = "Effacer les journaux"
    override val readingLog = "Lecture des journaux…"
    override val emptyLog = "(Aucun journal)"
    override val logShareSubject = "Journaux K Agenda"
    override val shareChooseFormat = "Partager les journaux"
    override val shareFormatHint = "Un fichier de journal (nom anglais + horodatage) sera généré puis partagé. Choisissez le format :"
    override val shareAsTxt = "Texte TXT"
    override val shareAsMd = "Markdown"
    override val logMdTime = "Généré le"
    override val logMdVersion = "Version de l'appli"
    override val logMdDevice = "Appareil"
    override val diagTitle = "Diagnostics"
    override val diagVersion = "Version de l'application"
    override val diagSync = "État de synchro"
    override val diagLastSync = "Dernière synchro"
    override val diagNone = "Aucun"

    override val msgNeedStudentId = "Saisissez votre identifiant"
    override val msgAccountSaved = "Compte enregistré, synchronisation…"
    override val msgAccountSavedKeepPwd = "Identifiant enregistré (aucun nouveau mot de passe : synchro avec le mot de passe enregistré, non vérifié)"
    override val msgLoggedOut = "Déconnecté : compte et session web effacés"
    override val msgCredDeleted = "Identifiants supprimés (session web et cache conservés)"
    override val msgResetDone = "Application réinitialisée : reconnectez-vous"
    override val msgEventsCleared = "Événements et plans effacés"
    override val msgCacheLogsCleared = "Cache et journaux effacés"
    override val msgCacheCleared = "Cache de l'emploi du temps effacé"
    override val msgReminderOn = "Rappels de cours activés"
    override val msgReminderOff = "Rappels de cours désactivés"
    override val msgNoScheduleData = "Aucune donnée — synchronisez d'abord"
    override val msgSaveFailed = "Échec de l'enregistrement, réessayez"
    override val msgStorageNeedPerm =
        "Permission de stockage refusée : l'image n'a pas pu être enregistrée dans la galerie " +
            "(nécessaire sous Android 8.0–9)"
    override val msgSavedDayImage = "Jour enregistré dans la galerie (Pictures/K日程)"
    override val msgSavedWeekImage = "Semaine enregistrée dans la galerie (Pictures/K日程)"
    override val msgSavedMonthImage = "Mois enregistré dans la galerie (Pictures/K日程)"

    override val channelName = "Rappels de cours"
    override val channelDesc = "Rappels avant chaque cours"
    override fun notifClassSoon(title: String) = "Cours bientôt : ${title}"
    override fun notifAgendaSoon(title: String) = "Bientôt : ${title}"
    override val syncTimeLabel = "Synchro : "
    override val exportTimeLabel = "Exporté : "

    override val backToToday = "Revenir à aujourd'hui"
    override val backToThisWeek = "Revenir à cette semaine"

    override val secPeriodTimes = "Horaires des cours"
    override val secPeriodTimesSub = "Personnalisez le début/la fin de chaque période (14 par défaut)"
    override val btnEditPeriodTimes = "Personnaliser les horaires"
    override val periodTimesTitle = "Horaires des cours"
    override val periodTimesHint = "Ajoutez ou supprimez des périodes (1–24) ; saisissez les heures au format 24 h HH:mm (aussi 800 / 0800 / 8.00). Les vues jour/semaine, le widget et la notification permanente sont mis à jour immédiatement."
    override val periodTimesRestoreDefault = "Rétablir par défaut"
    override val periodTimesRowFmt = "Période %d"
    override fun periodTimesCount(n: Int) = "$n période(s)"
    override fun periodTimesCountWarning(maxUsed: Int, configured: Int) =
        "Attention : l'emploi du temps va jusqu'à la période $maxUsed, mais seulement $configured sont configurées ici — les cours en trop ne s'afficheront pas dans la vue semaine."
    override val periodTimesAddOne = "+ Ajouter une période"
    override val periodTimesRemoveOne = "Supprimer cette période"
    override val msgPeriodTimesSaved = "Horaires mis à jour"
    override val msgPeriodTimesInvalid = "Format invalide — remplissez chaque période au format HH:mm"
    override val periodTimesDefaultTag = "Horaires par défaut"
    override val periodTimesCustomTag = "Horaires personnalisés"

    override val secLockScreen = "Afficher sur l'écran verrouillé"
    override val secLockScreenSub = "Désactivé, la notification permanente n'apparaît pas sur l'écran verrouillé"
    override val btnOpenNotifSettings = "Ouvrir les réglages de notifications"
    override val lockScreenHint = "Notification absente de l'écran verrouillé ? Certains systèmes (MIUI/HyperOS, EMUI) masquent les notifications silencieuses — activez « Notifications sur écran verrouillé » pour cette application."

    override val secUiStyle = "Apparence"
    override val secUiStyleSub = "Choisissez le style visuel global (par défaut : style actuel conservé)"
    override val uiStyleDefault = "Par défaut (minimal)"
    override val uiStyleDefaultSub = "Le style actuel à cartes opaques"
    override val uiStyleGlass = "Verre liquide"
    override val uiStyleGlassSub = "Fond dégradé + cartes translucides dépoli, coins plus doux"

    override val secDocs = "Import de documents"
    override val secDocsSub = "Lire Word / Excel / PPT / PDF / texte et détecter emplois du temps, événements et plans"
    override val docTitle = "Import de documents"
    override val docIntro = "Choisissez un document : le texte est extrait sur cet appareil et les emplois du temps, événements et plans sont détectés automatiquement (rien n'est envoyé)."
    override val docOpenHint = "Ouvrir un fichier avec « Ouvrir avec K日程 » depuis une autre application mène aussi ici."
    override val docPick = "Choisir un document"
    override val docReading = "Lecture du document…"
    override fun docReadFail(msg: String) = "Échec de lecture : $msg"
    override fun docUnsupportedExt(ext: String) = "Le format .$ext n'est pas pris en charge (réenregistrez en docx / xlsx / pdf / txt)"
    override val docNoText = "Aucun texte extrait (les PDF scannés ou les documents image nécessitent un copier-coller)"
    override val docTextLabel = "Texte du document (modifiable, puis relancer la détection)"
    override fun docDetected(events: Int, courses: Int) = "Détecté : $events événement(s)/plan(s), $courses cours"
    override val docImportAgenda = "Importer comme événements"
    override val docImportPlan = "Importer comme plans"
    override val docImportCourses = "Importer comme emploi du temps"
    override fun docAnchorLabel(date: String) = "Lundi de la semaine 1 : $date"
    override val docAnchorHint = "Les semaines des cours sont calculées depuis cette date ; ajustez si besoin"
    override fun docImportedList(n: Int) = "Cours importés ($n)"
    override val docClearImported = "Vider les cours importés"
    override val docExport = "Exporter en document"
    override val docExportHint = "Exporter l'emploi du temps et les événements en CSV / Markdown vers l'emplacement choisi"
    override val docExportCsv = "Exporter CSV"
    override val docExportMd = "Exporter Markdown"
    override val docExported = "Exporté vers l'emplacement choisi"
    override val docExportFailed = "Échec de l'export"
    override fun msgImportedCourses(n: Int) = "$n cours importé(s)"
    override val msgImportedCoursesCleared = "Cours importés vidés"
    override val msgImportNoCourses = "Aucun cours détecté — vérifiez que le document contient le jour et la période"
    override val msgImportNoEvents = "Aucun événement ou plan détecté"
    override val docLocalOnly = "Tout se passe sur cet appareil ; rien n'est envoyé en ligne"

    override val aiOfflineParse = "Détection locale"
    override fun aiOfflineDone(n: Int) = "$n élément(s) détecté(s) localement (sans IA)"

    // ---------------- Modifications de cours ----------------
    override val tagEdited = "Modifié"
    override val btnEditCourse = "Modifier le cours"
    override val courseEditTitle = "Modifier le cours"
    override val courseEditIntro =
        "À utiliser quand les informations du cours ont changé mais que le système scolaire ne les a pas encore mises à jour. Les modifications restent sur cet appareil et ne touchent pas le système scolaire."
    override val editScopeLabel = "Appliquer à"
    override val editScopeOne = "Seulement cette occurrence"
    override val editScopeOneHint = "Modifier uniquement ce bloc (jour et périodes modifiables aussi)"
    override val editScopeAll = "Toutes les occurrences de ce cours"
    override val editScopeAllHint = "Chaque bloc de ce cours recevra la même modification"
    override val fieldCourseName = "Nom du cours"
    override val editWeeksHint = "ex. 2-17 ; vide = chaque semaine"
    override val editPeriodStart = "Période de début"
    override val editPeriodEnd = "Période de fin"
    override val editNoChange = "Aucune modification détectée"
    override val editSavedNote = "Modifié sur cet appareil, pas encore synchronisé avec le système scolaire"
    override val msgCourseEditSaved = "Modification enregistrée et marquée comme « Modifié »"
    override val msgImportedCourseEdited = "Cours importé modifié"
    override val msgCourseEditsRestored = "Toutes les modifications de cours sont restaurées"
    override val msgCourseEditsClearedSyncing = "Modifications effacées, synchronisation en cours…"
    override val msgEditFollowed = "Aligné sur le système scolaire"
    override val msgEditKept = "Votre modification est conservée"
    override fun msgEditConflicts(n: Int) = "$n cours diffèrent du système scolaire — à vérifier"

    override val secCourseEdits = "Modifications de cours"
    override val secCourseEditsSub = "Corrections locales quand le système scolaire est en retard"
    override fun courseEditsCount(n: Int) = "$n modifié(s)"
    override val courseEditsNone = "Aucune modification de cours pour l'instant"
    override val courseEditsIntro =
        "Touchez une carte de cours dans l'emploi du temps pour modifier ses informations. Quand le système scolaire correspondra à votre modification, la marque « Modifié » disparaît automatiquement."
    override val btnRestoreCourseEdits = "Restaurer uniquement les modifications"
    override val btnSyncClearCourseEdits = "Synchroniser et effacer toutes les modifications"
    override val courseEditsHint =
        "« Restaurer » remet l'emploi du temps à la version du système scolaire (hors ligne). « Synchroniser et effacer » supprime toutes les modifications puis récupère l'emploi du temps."
    override val askRestoreEditsTitle = "Restaurer toutes les modifications ?"
    override val askRestoreEditsBody =
        "Chaque cours revient à la version enregistrée par le système scolaire et les marques « Modifié » disparaissent."
    override val askSyncClearEditsTitle = "Synchroniser et effacer les modifications ?"
    override val askSyncClearEditsBody =
        "Toutes les modifications locales seront supprimées et l'emploi du temps récupéré à nouveau ; les données du système scolaire s'appliquent."

    override val editConflictTitle = "Informations différentes du système scolaire"
    override val editConflictIntro =
        "Le système scolaire a mis à jour ces cours, différemment de votre modification. Suivre le système scolaire ?"
    override val editConflictLatest = "Système scolaire (actuel)"
    override val editConflictYours = "Votre modification"
    override val editConflictMissing = "Ce cours n'existe plus dans le système scolaire"
    override val btnFollowAcademic = "Suivre le système"
    override val btnKeepMine = "Garder ma modification"
    override val btnFollowAll = "Tout suivre le système"
    override val btnKeepAll = "Garder toutes mes modifications"
    override val editConflictLater = "Plus tard"
    // ---------------- Modification manuelle : supprimer / ajouter / déplacer ----------------
    override val editActionModify = "Modifier"
    override val editActionDelete = "Supprimer le cours"
    override val editActionMove = "Déplacer"
    override val editDeleteIntro =
        "Le cours sera masqué pour les semaines sélectionnées (local uniquement, le système scolaire n'est pas modifié)."
    override val editDeleteWeeksHint = "Tout sélectionner = masquer tout le cours"
    override fun editMoveIntro(n: Int) =
        "Déplacer les cours de ce jour (tous ou un seul) vers une autre date (local uniquement). Ce jour compte $n cours."
    override val editMoveWholeDay = "Toute la journée"
    override fun editMoveWholeDayHint(n: Int) = "Les $n cours de ce jour seront déplacés vers la date cible"
    override val editMoveSingle = "Plusieurs cours"
    override val editMoveSingleHint = "Cochez les cours à déplacer (multi-sélection) ; les autres restent"
    override val editMoveTarget = "Date cible"
    override val editMovePickDate = "Choisir une date"
    override val weeksAll = "Toutes"
    override val weeksNone = "Effacer"
    override val courseAddTitle = "Ajouter un cours"
    override val courseAddIntro =
        "Ajoutez un cours absent du système scolaire (local uniquement, rien n'est envoyé)."
    override val btnAddCourse = "Ajouter un cours"
    override val btnCopyCourse = "Copier le cours"
    override val aboutOfficialSite = "Site officiel / téléchargement"
    override val aboutGithubRepo = "Dépôt GitHub"

    override val secFeedback = "Retour utilisateur"
    override val secFeedbackSub = "Bugs, idées ou adaptation d'école"
    override val feedbackIntro =
        "Le retour est envoyé au site du développeur (20071009.xyz). N'incluez jamais vos identifiants."
    override val feedbackTopic = "Sujet"
    override val topicBug = "Signaler un bug"
    override val topicFeature = "Proposer une idée"
    override val topicSchool = "Adaptation d'école"
    override val topicOther = "Autre"
    override val feedbackPlaceholder = "Décrivez le problème ou l'idée..."
    override val feedbackContact = "Contact (facultatif)"
    override val btnFeedbackSend = "Envoyer"
    override val msgFeedbackSent = "Retour envoyé, merci !"
    override val msgFeedbackFailed = "Échec de l'envoi ; vérifiez le réseau"
    override val feedbackEmpty = "Écrivez d'abord votre message"
    override val feedbackIncludeNote =
        "Joint automatiquement : version, version d'Android, école (sans identifiant, mot de passe ni contenu de cours)"
    override val btnRescheduleDate = "Déplacer vers..."
    override val longPressHint = "Appui long sur une date pour déplacer les cours"
    override val rescheduleTitle = "Déplacer vers..."
    override fun rescheduleIntro(month: Int, day: Int, n: Int) =
        "Le $month/$day compte $n cours. Choisissez les cours à déplacer et la date cible."
    override val rescheduleSingleHint = "Déplacer un seul cours (choisir ci-dessous)"
    override val msgCourseDeleted = "Supprimé (local uniquement)"
    override val msgCourseAdded = "Cours ajouté (local uniquement)"
    override fun msgCourseMoved(n: Int, month: Int, day: Int) = "$n cours déplacés au $month/$day"
    override val msgMoveSameDay = "La date cible est identique à la source"
    override val msgMoveNoWeek = "Semaine introuvable ; synchronisez d'abord l'emploi du temps"

    override val secHolidayCourses = "Cours les jours fériés"
    override val secHolidayCoursesSub = "Désactivé par défaut (cours masqués)"
    override val holidayCoursesSwitch = "Afficher les cours les jours fériés"
    override val holidayCoursesNote =
        "Activé, les cours des jours fériés s'affichent et les rappels fonctionnent (utile pour les déplacer)."

    override val secBackup = "Sauvegarde et restauration"
    override val secBackupSub = "Exportez avant de changer d'appareil ou de réinstaller"
    override val backupIntro =
        "Exportez un fichier de sauvegarde (cache d'emploi du temps, agenda et plans, modifications de cours, réglages) à restaurer après réinstallation."
    override val backupIncludes =
        "La sauvegarde contient l'identifiant étudiant, l'emploi du temps, l'agenda et les réglages ; pas le mot de passe ni la clé API (indéchiffrables sur un autre appareil)."
    override val btnBackupExport = "Exporter"
    override val btnBackupImport = "Importer"
    override val backupAskImportTitle = "Importer la sauvegarde ?"
    override val backupAskImportBody =
        "La sauvegarde remplacera l'emploi du temps, l'agenda et les réglages actuels (mot de passe et clé API conservés)."
    override val backupFileName = "KAgenda_sauvegarde"
    override val backupExportTitle = "Exporter la sauvegarde vers"
    override val backupExportDefault = "Emplacement par défaut (recommandé)"
    override val backupExportCustom = "Choisir un autre emplacement..."
    override fun msgBackupExportedDefault(dir: String, name: String) = "Sauvegarde enregistrée dans $dir/$name"
    override val backupImportTitle = "Importer la sauvegarde"
    override fun backupImportFound(n: Int, dir: String) = "$n sauvegarde(s) trouvée(s) dans $dir :"
    override val backupImportPickOther = "Choisir un autre fichier..."
    override fun backupDefaultDirNote(dir: String) =
        "Emplacement par défaut : $dir (conservé après réinstallation ; vérifié en premier)"
    override fun msgBackupExported(size: String) = "Sauvegarde exportée ($size)"
    override val msgBackupExportFailed = "Échec de l'export"
    override fun msgBackupImported(agenda: Int, edits: Int, courses: Int) =
        "Sauvegarde importée : $agenda éléments, $edits modifications, $courses cours importés"
    override val msgBackupInvalid = "Ce n'est pas une sauvegarde KAgenda"
    override fun msgBackupNewer(version: Int) = "Format v$version plus récent que l'application"
    override fun msgBackupFailed(detail: String) = "Échec de l'import : $detail"
}

// ==================================================================== 全局入口

object AppText {

    /** 当前语言选择（SYSTEM=跟随系统）；UI 用 collectAsState 观察 */
    val state = MutableStateFlow(AppLang.SYSTEM)

    /** 把 SYSTEM 解析为具体语言：中文→ZH，法语→FR，其他→EN */
    fun resolve(lang: AppLang): AppLang {
        if (lang != AppLang.SYSTEM) return lang
        return when (Locale.getDefault().language) {
            "zh" -> AppLang.ZH
            "fr" -> AppLang.FR
            else -> AppLang.EN
        }
    }

    fun stringsFor(lang: AppLang): AppStrings = when (resolve(lang)) {
        AppLang.ZH -> ZhStrings
        AppLang.FR -> FrStrings
        else -> EnStrings
    }

    /** 当前语言对应的文案（供 ViewModel / 渲染器 / 通知等非 Compose 场景使用） */
    val current: AppStrings get() = stringsFor(state.value)

    /**
     * 同步到系统「按应用设置语言」（影响桌面图标名称等系统侧文案）。
     * SYSTEM → 空列表（跟随系统）。
     */
    fun applySystemLocale(context: Context, lang: AppLang) {
        // 系统级「按应用设置语言」为 Android 13+；更低版本仅应用内文案即时切换
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            val lm = context.getSystemService(LocaleManager::class.java) ?: return
            lm.applicationLocales = when (lang) {
                AppLang.SYSTEM -> LocaleList.getEmptyLocaleList()
                AppLang.ZH -> LocaleList.forLanguageTags("zh-CN")
                AppLang.FR -> LocaleList.forLanguageTags("fr")
                AppLang.EN -> LocaleList.forLanguageTags("en")
            }
        }
    }
}

/** Compose 侧文案入口：CompositionLocalProvider(LocalStrings provides ...) */
val LocalStrings = staticCompositionLocalOf<AppStrings> { AppText.current }
