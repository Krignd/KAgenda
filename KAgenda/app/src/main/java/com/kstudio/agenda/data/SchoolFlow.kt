package com.kstudio.agenda.data

import android.content.Context
import android.webkit.WebView
import com.kstudio.agenda.i18n.AppStrings
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.School
import java.time.LocalTime

/**
 * 学校流程插件 —— 「学校差异」的唯一归属地。
 *
 * ## 为什么要有这一层
 *
 * 每所学校的教务系统都不一样：探针脚本、提取脚本、登录方式、课表页地址、
 * 作息节数、甚至「是否需要用户自己拖滑块登录」。这些差异如果直接写在通用代码里
 * （典型写法 `if (school.id == "xxx") …`），就会出现用户最担心的那种情况：
 * **为了适配 A 校改了通用流程，结果把 B 校（如北航）改坏了。**
 *
 * 本层把差异收敛到「每校一个插件对象 + 一份注册表」：
 * - 通用代码（[WebScheduleEngine] / WebLoginActivity / SettingsScreen …）只面向本接口；
 * - 新增学校 = 新写一个实现（建议单独成文件）+ 在 [SchoolFlows] 注册一行 + 在
 *   `Schools.ALL` 加一条数据；**不需要改动任何通用文件**，因此不会波及其他学校。
 *
 * ## 各成员与「通用默认值」的关系
 *
 * 每个成员都有默认实现，意味着「不写 = 走通用逻辑」；只有真正需要差异的学校
 * 才覆写对应方法。当前已注册的学校见 [SchoolFlows.registry]。
 */
internal interface SchoolFlow {

    /**
     * 同步成功后并入课表的「补充课程」（教务页面里没有、需要人工维护的条目）。
     *
     * 默认不追加任何内容；只有声明了补充课程的学校（如江苏大学的晚自习）才覆写。
     */
    fun supplements(school: School): List<Course> = emptyList()

    /**
     * 「必须手动登录」的学校的同步实现。
     *
     * 默认返回 null（表示本插件不接管，引擎给出明确失败提示）。覆写它即意味着
     * 该校的整条抓取流程都由本插件负责，引擎不再插手。
     * [school.manualLogin] 为 true 时引擎才会调用本方法。
     */
    suspend fun syncManually(host: ManualSyncHost, view: WebView): SyncResult? = null

    /**
     * 开发者工具「内置浏览器」的起始页。默认是该校教务首页。
     *
     * 需要先经过门户/统一身份的学校可以覆写（例如先打开门户登录页）。
     */
    fun devBrowserStartUrl(school: School): String = school.homeUrl

    /**
     * 该校的分段作息预设（如江苏大学 11 节）。
     *
     * 仅在用户**没有自定义过**课程时间时生效（见 `PeriodTimes.applySchoolPreset`）；
     * 默认 null 表示用内置默认作息（北航等沿用默认的学校无需改这里）。
     */
    fun periodPreset(school: School): List<Pair<LocalTime, LocalTime>>? = null

    /**
     * 清浏览器会话（重置应用 / 退出登录）时要审计的域名列表。
     *
     * 只记录这些域下**剩余 Cookie 的名字**（不记值），用于确认「重置后确实回到未登录」。
     * 默认空（通用学校无此需要）；多主机/门户型学校可覆写。
     */
    fun cookieAuditOrigins(school: School): List<String> = emptyList()

    /**
     * 设置页「学校 & 账号」里的学校专属开关（默认无）。
     *
     * 例如江苏大学的「通过 WebVPN」：勾选与否对应两套完全隔离的访问链路。
     */
    val schoolToggle: SchoolToggleSpec? get() = null
}

/**
 * 设置页里的学校专属开关描述。
 *
 * 通用设置界面只负责渲染（拿到当前值、写回），具体语义完全由学校流程定义，
 * 因此通用代码里不会出现任何学校名字。
 */
internal data class SchoolToggleSpec(
    /** 日志用标识（如 "ujs_webvpn"） */
    val id: String,
    /** 读取当前值（来自设置快照，供 Compose 直接绑定） */
    val valueOf: (AppSettings) -> Boolean,
    /** 写回设置 */
    val set: suspend (Context, Boolean) -> Unit,
    /** 标题（三语，由 i18n 提供） */
    val title: (AppStrings) -> String,
    /** 说明文字（三语） */
    val subtitle: (AppStrings) -> String,
)

/**
 * 引擎向学校流程暴露的**最小能力集**。
 *
 * 学校流程拿到的只有这些通用能力（探针 / 等待 / 执行 JS / 提取 / 计数），
 * 既不持有引擎内部可变状态，也不会绕开引擎直接操作 WebView 生命周期。
 */
internal interface ManualSyncHost {
    val school: School
    val appContext: Context

    /** 读取当前页面状态（按 `school.probeJs` 或通用探针） */
    suspend fun probePage(view: WebView): PageProbe?

    /** 轮询等待页面进入某状态；超时返回最后一次探针结果 */
    suspend fun waitFor(view: WebView, timeoutMs: Long, predicate: (PageProbe) -> Boolean): PageProbe?

    /** 执行 JS 并取回字符串（脚本需自行 JSON.stringify） */
    suspend fun evaluateJs(view: WebView, script: String): String?

    /** 执行适配器提取脚本并把返回值解析为课表（含补充课程合并） */
    suspend fun customExtract(view: WebView, script: String): SyncResult

    /** 统计页面已渲染的课程块数量（判断异步课表是否加载完成） */
    suspend fun countCourseBlocks(view: WebView): Int
}

/** 页面状态探针结果（引擎与学校流程共用同一份定义，避免各写一套） */
internal data class PageProbe(
    val url: String = "",
    val hasLoginForm: Boolean = false,
    val captcha: Boolean = false,
    val hasGrid: Boolean = false,
    val hasApp: Boolean = false,
    val notFound: Boolean = false,
    val netError: Boolean = false,
    val ssoError: Boolean = false,
    val error: String = "",
    val snippet: String = "",
) {
    fun summary(): String = buildString {
        append("login=$hasLoginForm captcha=$captcha grid=$hasGrid app=$hasApp netError=$netError")
        if (notFound) append(" notFound=true")
        if (ssoError) append(" ssoError=true")
        if (error.isNotBlank()) append(" error=").append(error)
        append(" url=").append(url.removePrefix("https://").take(90))
    }
}

/**
 * 学校流程注册表 —— **新增学校时唯一需要修改的通用位置**（加一行即可）。
 *
 * 未登记的学校统一走 [DefaultSchoolFlow]（全部默认行为，即原有逻辑）。
 */
internal object SchoolFlows {

    /** schoolId → 学校流程插件。键必须与 `Schools.ALL` 里的 `id` 完全一致。 */
    private val registry: Map<String, SchoolFlow> = mapOf(
        // 江苏大学：正方教务 + WebVPN 门户（手动登录），见 SchoolFlowUjs.kt
        UjsFlowPlugin.SCHOOL_ID to UjsFlowPlugin,
    )

    /** 未登记学校的默认流程：不改变任何通用行为 */
    private object DefaultSchoolFlow : SchoolFlow

    fun of(school: School): SchoolFlow = of(school.id)

    fun of(schoolId: String?): SchoolFlow =
        if (schoolId == null) DefaultSchoolFlow else registry[schoolId] ?: DefaultSchoolFlow
}
