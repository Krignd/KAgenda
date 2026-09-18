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
    fun secAccountSubOf(schoolId: String, schoolName: String): String
    val labelStudentId: String
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
    val askClearCredTitle: String
    val askClearCredBody: String

    val secReminder: String
    val secReminderSub: String
    val reminderEnable: String
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
    val qaPlanTag: String
    val qaAgendaTag: String
    fun qaAdded(n: Int): String

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
    val statusHint: String
    val statusChannelName: String
    val statusChannelDesc: String
    fun statusInClassFmt(name: String, end: String): String
    fun statusNextClassFmt(name: String, time: String, left: String): String
    fun statusOngoingPlanFmt(name: String): String
    fun statusNextAgendaFmt(name: String, time: String): String
    val statusAiEntryTitle: String
    val statusAiEntryHint: String
    val notifOk: String
    val notifNo: String
    val enableNotif: String

    val secData: String
    val secDataSub: String
    val autoRefresh: String
    val btnSyncNow: String
    val btnClearCache: String
    val btnSaveDayImg: String
    val btnSaveWeekImg: String
    val imgDirNote: String

    val secLang: String
    val langSystem: String

    val secDev: String
    val secDevSub: String
    val openDevTools: String
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
    val resetAll: String
    val resetAllDesc: String
    val askResetTitle: String
    val askResetBody: String
    val askClearDataTitle: String
    val askClearDataBody: String
    val askClearCacheTitle: String
    val askClearCacheBody: String

    val secAbout: String
    val aboutSub: String
    val aboutBody: String
    val aboutTip: String
    val aboutPeriods: String

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
    val diagTitle: String
    val diagVersion: String
    val diagSync: String
    val diagLastSync: String
    val diagNone: String

    // ---------------- 提示消息（ViewModel） ----------------
    val msgNeedStudentId: String
    val msgAccountSaved: String
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
    val msgSavedDayImage: String
    val msgSavedWeekImage: String
    val msgSavedMonthImage: String

    // ---------------- 通知 / 导出 ----------------
    val channelName: String
    val channelDesc: String
    fun notifClassSoon(title: String): String
    val syncTimeLabel: String
    val exportTimeLabel: String
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

    override fun ongoingHeader(n: Int) = "正在进行的长日程（${n}）"
    override val ongoingEmptyTitle = "暂无正在进行的长日程"
    override val ongoingEmptyHint =
        "长日程用于跨天的时间段（如考试报名 9/10 10:00 ~ 10/30 22:00）。" +
            "可在「日程表」或「计划」页点「添加」按钮，选择“长日程”创建。"
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "剩余 ${days}天${hours}小时"
        hours > 0 -> "剩余 ${hours}小时${mins}分"
        else -> "剩余 ${mins}分"
    }

    override val secAccount = "账号"
    override fun secAccountSubOf(schoolId: String, schoolName: String) =
        if (schoolId == "buaa") "登录北航本研教育管理系统（byxt.buaa.edu.cn）"
        else "登录${schoolName}的教务系统"
    override val labelStudentId = "学号"
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
    override val askClearCredTitle = "删除保存的账密？"
    override val askClearCredBody = "将删除本机保存的学号与密码；网页登录会话与课表缓存保留。之后可随时重新输入保存。"

    override val secReminder = "课前提醒"
    override val secReminderSub = "在每节课开始前推送通知提醒"
    override val reminderEnable = "开启提醒"
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
    override val adapterStageRequest = "正在请求 DeepSeek 生成适配代码…"
    override val adapterStageParse = "正在解析生成结果…"
    override val adapterStageDone = "已生成适配代码，请核对后保存"
    override fun adapterWaiting(sec: Int) = "（已等待 $sec 秒，请耐心等待）"

    // AI 快速添加（悬浮入口）
    override val qaTitle = "AI 快速添加"
    override val qaHint = "粘贴或输入文字（可含多个日程/计划），AI 自动识别并添加"
    override val qaParse = "AI 识别"
    override val qaConfirm = "全部添加"
    override val qaNothing = "未识别到可添加的内容"
    override val qaPlanTag = "计划"
    override val qaAgendaTag = "日程"
    override fun qaAdded(n: Int) = "已添加 $n 项"

    override val secOverlay = "悬浮球"
    override val secOverlaySub = "在任意应用上方显示 DeepSeek 悬浮球，点按即可输入并添加日程"
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
    override val secAiSub = "配置 API Key 后，可用一句话生成或修改日程/计划"
    override val aiKeyLabel = "DeepSeek API Key"
    override val aiKeyKeepHint = "已保存（留空则不修改）"
    override val aiUseDevKey = "使用开发者的 API key"
    override val aiDevKeyInUse = "已启用内置 API Key，无需自行申请即可使用 AI 识别"
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
    override val statusSrcPlan = "当前计划"
    override val statusSrcAgenda = "下一日程"
    override val statusHint = "可多选；选中项会常驻显示在锁屏与通知栏"
    override val statusChannelName = "状态常驻"
    override val statusChannelDesc = "锁屏与通知栏常驻显示课表/日程状态"
    override fun statusInClassFmt(name: String, end: String) = "正在上课：$name · 至 $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "下一节课：$name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "进行中计划：$name"
    override fun statusNextAgendaFmt(name: String, time: String) = "下一日程：$name · $time"
    override val statusAiEntryTitle = "AI 快速添加"
    override val statusAiEntryHint = "点按输入文字 · 自动识别并添加日程"
    override val notifOk = "通知权限：已开启"
    override val notifNo = "通知权限：未开启"
    override val enableNotif = "开启通知"

    override val secData = "数据与图片"
    override val secDataSub = "课表从教务系统同步后缓存在本机"
    override val autoRefresh = "打开应用时自动刷新"
    override val btnSyncNow = "立即同步"
    override val btnClearCache = "清除缓存"
    override val btnSaveDayImg = "保存日课表图片"
    override val btnSaveWeekImg = "保存周课表图片"
    override val imgDirNote = "图片会保存到系统相册的 Pictures/K日程 目录。"

    override val secLang = "语言"
    override val langSystem = "跟随系统"

    override val secDev = "开发者工具"
    override val secDevSub = "运行日志与诊断信息"
    override val openDevTools = "打开开发者工具"
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
    override val resetAll = "重置应用（恢复初始状态）"
    override val resetAllDesc = "将清除账号、网页会话、课表缓存、日程与计划、提醒与闹钟、日志及语言等设置。"
    override val askResetTitle = "确认重置？"
    override val askResetBody = "所有数据与设置将被清除，应用回到初始状态。"
    override val askClearDataTitle = "清除全部日程与计划？"
    override val askClearDataBody = "本地日程与计划将被全部删除，此操作不可撤销。"
    override val askClearCacheTitle = "清除缓存与日志？"
    override val askClearCacheBody = "将删除课表缓存与运行日志。"

    override val secAbout = "关于"
    override val aboutSub = "K日程 ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "本地优先的课表与日程应用：课表从学校教务系统同步后缓存在本机，" +
            "支持日/周/月视图（左右滑动切换）、日程与计划、课前提醒、桌面小组件与常驻通知；" +
            "内置 DeepSeek AI 快速添加（顶栏按钮或悬浮球）。" +
            "除用户自行配置的 AI 接口外，不上传任何个人信息。"
    override val aboutTip = "提示：若同步失败而网页可正常访问，多为学校页面改版——北航可到「开发者工具」分享运行日志；其他学校可在「设置 → 学校 → 添加学校」更新适配代码。"
    override val aboutPeriods = "课程时间表：上午 4 节 + 下午 5 节 + 晚上 5 节（共 14 节），与教务处作息一致。"

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
    override val diagTitle = "诊断信息"
    override val diagVersion = "应用版本"
    override val diagSync = "同步状态"
    override val diagLastSync = "最近同步"
    override val diagNone = "无"

    override val msgNeedStudentId = "请输入学号"
    override val msgAccountSaved = "账号已保存，正在同步…"
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
    override val msgSavedDayImage = "日课表已保存到相册（Pictures/K日程）"
    override val msgSavedWeekImage = "周课表已保存到相册（Pictures/K日程）"
    override val msgSavedMonthImage = "月课表已保存到相册（Pictures/K日程）"

    override val channelName = "上课提醒"
    override val channelDesc = "上课前提醒通知"
    override fun notifClassSoon(title: String) = "即将上课：${title}"
    override val syncTimeLabel = "数据同步时间："
    override val exportTimeLabel = "导出时间："
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

    override fun ongoingHeader(n: Int) = "Ongoing long events (${n})"
    override val ongoingEmptyTitle = "No ongoing long events"
    override val ongoingEmptyHint =
        "Long events span multiple days (e.g. exam registration 9/10 10:00 – 10/30 22:00). " +
            "Create one from Schedule or Plans with \"Long term\" selected."
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${mins}m left"
        else -> "${mins}m left"
    }

    override val secAccount = "Account"
    override fun secAccountSubOf(schoolId: String, schoolName: String) =
        if (schoolId == "buaa") "Sign in to BUAA's academic system (byxt.buaa.edu.cn)"
        else "Sign in to $schoolName's academic system"
    override val labelStudentId = "Student ID"
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
    override val askClearCredTitle = "Delete saved credentials?"
    override val askClearCredBody = "The local student ID and password will be deleted; web session and schedule cache are kept. You can save them again anytime."

    override val secReminder = "Class reminder"
    override val secReminderSub = "Get a notification before each class"
    override val reminderEnable = "Enable reminders"
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
    override val adapterStageRequest = "Requesting DeepSeek to generate the adapter…"
    override val adapterStageParse = "Parsing the generated code…"
    override val adapterStageDone = "Adapter generated — review and save"
    override fun adapterWaiting(sec: Int) = " (waiting ${sec}s…)"

    // AI 快速添加（悬浮入口）
    override val qaTitle = "AI quick add"
    override val qaHint = "Paste or type text (multiple events/plans allowed); AI detects and adds them"
    override val qaParse = "AI parse"
    override val qaConfirm = "Add all"
    override val qaNothing = "Nothing to add"
    override val qaPlanTag = "Plan"
    override val qaAgendaTag = "Event"
    override fun qaAdded(n: Int) = "Added $n item(s)"

    override val secOverlay = "Floating ball"
    override val secOverlaySub = "Show a DeepSeek ball over other apps; tap to type and add schedules"
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
    override val secAiSub = "With an API key, create or edit events/plans in natural language"
    override val aiKeyLabel = "DeepSeek API Key"
    override val aiKeyKeepHint = "Saved (leave blank to keep)"
    override val aiUseDevKey = "Use developer's API key"
    override val aiDevKeyInUse = "Built-in API key enabled — AI works out of the box"
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
    override val statusSrcPlan = "Ongoing plan"
    override val statusSrcAgenda = "Next event"
    override val statusHint = "Multi-select; shown silently on lock screen"
    override val statusChannelName = "Status"
    override val statusChannelDesc = "Always-on status of classes & events"
    override fun statusInClassFmt(name: String, end: String) = "In class: $name · until $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "Next class: $name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "Ongoing plan: $name"
    override fun statusNextAgendaFmt(name: String, time: String) = "Next event: $name · $time"
    override val statusAiEntryTitle = "AI quick add"
    override val statusAiEntryHint = "Tap to type · auto-detect and add schedules"
    override val notifOk = "Notifications: enabled"
    override val notifNo = "Notifications: disabled"
    override val enableNotif = "Enable"

    override val secData = "Data & images"
    override val secDataSub = "Schedule is cached locally after sync"
    override val autoRefresh = "Auto-refresh on open"
    override val btnSyncNow = "Sync now"
    override val btnClearCache = "Clear cache"
    override val btnSaveDayImg = "Save day image"
    override val btnSaveWeekImg = "Save week image"
    override val imgDirNote = "Images are saved to Pictures/K日程 in your gallery."

    override val secLang = "Language"
    override val langSystem = "System default"

    override val secDev = "Developer tools"
    override val secDevSub = "Logs & diagnostics"
    override val openDevTools = "Open developer tools"
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
    override val resetAll = "Reset app"
    override val resetAllDesc = "Clears account, web session, schedule cache, events & plans, reminders, logs and language settings."
    override val askResetTitle = "Reset the app?"
    override val askResetBody = "All data and settings will be cleared."
    override val askClearDataTitle = "Clear all events and plans?"
    override val askClearDataBody = "All local events and plans will be deleted. This cannot be undone."
    override val askClearCacheTitle = "Clear cache and logs?"
    override val askClearCacheBody = "The schedule cache and runtime logs will be deleted."

    override val secAbout = "About"
    override val aboutSub = "K Agenda ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "Local-first timetable & agenda app: your schedule is fetched from the school's academic system and cached on-device. " +
            "Day/week/month views (swipe to switch), local events & plans, class reminders, home-screen widgets, " +
            "persistent status notification, and a DeepSeek AI quick add (toolbar button or floating ball over other apps). " +
            "No personal data is uploaded except to the AI endpoint you configure."
    override val aboutTip = "Note: if syncing fails while the website works, the school pages may have changed. BUAA users can share the runtime log from Developer tools; other schools can update the adapter code in Settings → School → Add school."
    override val aboutPeriods = "Periods: 4 morning + 5 afternoon + 5 evening (14 total), matching the academic affairs office."

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
    override val diagTitle = "Diagnostics"
    override val diagVersion = "App version"
    override val diagSync = "Sync status"
    override val diagLastSync = "Last sync"
    override val diagNone = "None"

    override val msgNeedStudentId = "Enter your student ID"
    override val msgAccountSaved = "Account saved, syncing…"
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
    override val msgSavedDayImage = "Day schedule saved to gallery (Pictures/K日程)"
    override val msgSavedWeekImage = "Week schedule saved to gallery (Pictures/K日程)"
    override val msgSavedMonthImage = "Month schedule saved to gallery (Pictures/K日程)"

    override val channelName = "Class reminders"
    override val channelDesc = "Reminder notifications before classes"
    override fun notifClassSoon(title: String) = "Class soon: ${title}"
    override val syncTimeLabel = "Synced: "
    override val exportTimeLabel = "Exported: "
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

    override fun ongoingHeader(n: Int) = "Événements longs en cours (${n})"
    override val ongoingEmptyTitle = "Aucun événement long en cours"
    override val ongoingEmptyHint =
        "Les événements longs couvrent plusieurs jours (ex. inscription 9/10 10:00 – 10/30 22:00). " +
            "Créez-en un depuis Agenda ou Plans en choisissant « Longue durée »."
    override fun remaining(days: Long, hours: Long, mins: Long): String = when {
        days > 0 -> "Reste ${days} j ${hours} h"
        hours > 0 -> "Reste ${hours} h ${mins} min"
        else -> "Reste ${mins} min"
    }

    override val secAccount = "Compte"
    override fun secAccountSubOf(schoolId: String, schoolName: String) =
        if (schoolId == "buaa") "Connexion au système académique de BUAA (byxt.buaa.edu.cn)"
        else "Connexion au système académique de $schoolName"
    override val labelStudentId = "Identifiant étudiant"
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
    override val askClearCredTitle = "Supprimer les identifiants ?"
    override val askClearCredBody = "L'identifiant et le mot de passe locaux seront supprimés ; la session web et le cache sont conservés."

    override val secReminder = "Rappel de cours"
    override val secReminderSub = "Une notification avant chaque cours"
    override val reminderEnable = "Activer les rappels"
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
    override val adapterStageRequest = "Demande d'adaptation à DeepSeek…"
    override val adapterStageParse = "Analyse du code généré…"
    override val adapterStageDone = "Adaptation générée — vérifiez puis enregistrez"
    override fun adapterWaiting(sec: Int) = " (en attente ${sec} s…)"

    // AI 快速添加（悬浮入口）
    override val qaTitle = "Ajout rapide IA"
    override val qaHint = "Collez ou saisissez un texte (plusieurs événements possibles) ; l'IA les ajoute"
    override val qaParse = "Analyse IA"
    override val qaConfirm = "Tout ajouter"
    override val qaNothing = "Rien à ajouter"
    override val qaPlanTag = "Plan"
    override val qaAgendaTag = "Événement"
    override fun qaAdded(n: Int) = "$n élément(s) ajouté(s)"

    override val secOverlay = "Bulle flottante"
    override val secOverlaySub = "Afficher la bulle DeepSeek par-dessus les autres applis ; appuyez pour saisir et ajouter"
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
    override val secAiSub = "Avec une clé API, créez/modifiez en langage naturel"
    override val aiKeyLabel = "Clé API DeepSeek"
    override val aiKeyKeepHint = "Enregistrée (laisser vide pour garder)"
    override val aiUseDevKey = "Utiliser la clé API du développeur"
    override val aiDevKeyInUse = "Clé API intégrée activée — l'IA est prête à l'emploi"
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
    override val statusSrcPlan = "Plan en cours"
    override val statusSrcAgenda = "Prochain événement"
    override val statusHint = "Multi-sélection ; silencieuse"
    override val statusChannelName = "Statut"
    override val statusChannelDesc = "Statut permanent des cours et événements"
    override fun statusInClassFmt(name: String, end: String) = "En cours : $name · jusqu'à $end"
    override fun statusNextClassFmt(name: String, time: String, left: String) = "Cours suivant : $name · $time · $left"
    override fun statusOngoingPlanFmt(name: String) = "Plan en cours : $name"
    override fun statusNextAgendaFmt(name: String, time: String) = "Prochain événement : $name · $time"
    override val statusAiEntryTitle = "Ajout rapide IA"
    override val statusAiEntryHint = "Appuyez pour saisir · ajout automatique des événements"
    override val notifOk = "Notifications : activées"
    override val notifNo = "Notifications : désactivées"
    override val enableNotif = "Activer"

    override val secData = "Données et images"
    override val secDataSub = "L'emploi du temps est mis en cache localement après synchro"
    override val autoRefresh = "Actualisation automatique à l'ouverture"
    override val btnSyncNow = "Synchroniser"
    override val btnClearCache = "Vider le cache"
    override val btnSaveDayImg = "Image du jour"
    override val btnSaveWeekImg = "Image de la semaine"
    override val imgDirNote = "Les images sont enregistrées dans Pictures/K日程."

    override val secLang = "Langue / Language"
    override val langSystem = "Langue du système"

    override val secDev = "Outils développeur"
    override val secDevSub = "Journaux et diagnostics"
    override val openDevTools = "Ouvrir les outils développeur"
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
    override val resetAll = "Réinitialiser l'application"
    override val resetAllDesc = "Efface compte, session web, cache, événements et plans, rappels, journaux et réglages de langue."
    override val askResetTitle = "Réinitialiser l'application ?"
    override val askResetBody = "Toutes les données et tous les réglages seront effacés."
    override val askClearDataTitle = "Effacer tous les événements et plans ?"
    override val askClearDataBody = "Action irréversible."
    override val askClearCacheTitle = "Vider le cache et les journaux ?"
    override val askClearCacheBody = "Le cache et les journaux seront supprimés."

    override val secAbout = "À propos"
    override val aboutSub = "K Agenda ${BuildConfig.VERSION_NAME}"
    override val aboutBody =
        "Application locale d'abord : l'emploi du temps est récupéré depuis le système académique de l'école et mis en cache sur l'appareil. " +
            "Vues jour/semaine/mois (balayage pour changer), événements et plans locaux, rappels de cours, widgets, " +
            "notification permanente et ajout rapide par IA DeepSeek (bouton ou bulle flottante au-dessus des autres applis). " +
            "Aucune donnée personnelle n'est envoyée, hormis vers l'API IA que vous configurez."
    override val aboutTip = "Remarque : si la synchro échoue alors que le site fonctionne, les pages de l'école ont peut-être changé. Les utilisateurs BUAA peuvent partager le journal via Outils développeur ; pour les autres écoles, mettez à jour le code d'adaptation dans Réglages → École → Ajouter une école."
    override val aboutPeriods = "Périodes : 4 le matin + 5 l'après-midi + 5 le soir (14 au total), conformes au calendrier universitaire."

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
    override val diagTitle = "Diagnostics"
    override val diagVersion = "Version de l'application"
    override val diagSync = "État de synchro"
    override val diagLastSync = "Dernière synchro"
    override val diagNone = "Aucun"

    override val msgNeedStudentId = "Saisissez votre identifiant"
    override val msgAccountSaved = "Compte enregistré, synchronisation…"
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
    override val msgSavedDayImage = "Jour enregistré dans la galerie (Pictures/K日程)"
    override val msgSavedWeekImage = "Semaine enregistrée dans la galerie (Pictures/K日程)"
    override val msgSavedMonthImage = "Mois enregistré dans la galerie (Pictures/K日程)"

    override val channelName = "Rappels de cours"
    override val channelDesc = "Rappels avant chaque cours"
    override fun notifClassSoon(title: String) = "Cours bientôt : ${title}"
    override val syncTimeLabel = "Synchro : "
    override val exportTimeLabel = "Exporté : "
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
