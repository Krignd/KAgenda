package com.kstudio.agenda.data

import android.os.SystemClock
import android.webkit.WebView
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.School
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 江苏大学 · 学校流程插件（正方教务 V-9.0 + WebVPN 门户）。
 *
 * 本文件是该学校**全部**差异的唯一归属地：
 * - 探针/提取脚本（[JsScripts.UJS_DETECT] / [JsScripts.UJS_EXTRACT]，脚本常量集中放在
 *   JsScripts.kt 末尾的「学校脚本区」，不修改上面的通用脚本）；
 * - 访问链路与回退候选（[UjsWebVpnFlow] / [UjsDefaultFlow]，见 data/UjsFlow.kt）；
 * - 手动登录后的抓取流程（本文件）；
 * - 作息预设（11 节）、补充课程（晚自习）、设置页开关（通过 WebVPN）。
 *
 * 通用代码（[WebScheduleEngine] / WebLoginActivity / SettingsScreen …）不含任何
 * 「ujs」字样，只通过 [SchoolFlows] 注册表拿到本插件。**因此以后调江苏大学的
 * 抓取逻辑，不会碰到北航等其它学校的代码路径。**
 */
internal object UjsFlowPlugin : SchoolFlow {

    const val SCHOOL_ID = "ujs"

    private const val TAG = "Engine"

    /** 登录完成后导航到课表页，等待其渲染课程的时间 */
    private const val SCHEDULE_RENDER_WAIT_MS = 30_000L

    /** 课表页表格首开为空：点「查询」后等待异步渲染的时间 */
    private const val QUERY_RENDER_WAIT_MS = 20_000L

    // ------------------------------------------------------------ 通用数据

    /**
     * 江大专用：教务页面之外的「补充课程」。
     *
     * 晚自习的教室安排不在正方课表里（课表页与存档数据中均无），由学员线下提供。
     * 三条均为**第 9-11 节 · 三江楼0711**：
     * - 第 5-6 周 周一
     * - 第 5-11 周 周四
     * - 第 12-16 周 周日
     *
     * 由引擎在提取成功后并入学期课表，与教务数据按课程 id 去重（同一门课以补充条目为准）。
     */
    override fun supplements(school: School): List<Course> = listOf(
        Course(title = "晚自习", code = "", teacher = "", weeksRaw = "5-6", room = "三江楼0711", startPeriod = 9, endPeriod = 11, dayOfWeek = 1, tag = "补充"),
        Course(title = "晚自习", code = "", teacher = "", weeksRaw = "5-11", room = "三江楼0711", startPeriod = 9, endPeriod = 11, dayOfWeek = 4, tag = "补充"),
        Course(title = "晚自习", code = "", teacher = "", weeksRaw = "12-16", room = "三江楼0711", startPeriod = 9, endPeriod = 11, dayOfWeek = 7, tag = "补充"),
    )

    /**
     * 江大作息预设（11 节）：上午 1-4 节、下午 5-8 节、晚上 9-11 节；
     * 第 2 节后有 20 分钟课间操。用户未自定义课程时间时自动套用。
     */
    override fun periodPreset(school: School): List<Pair<LocalTime, LocalTime>> = listOf(
        LocalTime.of(8, 0) to LocalTime.of(8, 45),    // 1
        LocalTime.of(8, 55) to LocalTime.of(9, 40),   // 2
        LocalTime.of(10, 10) to LocalTime.of(10, 55), // 3（2 节后有 20 分钟课间操）
        LocalTime.of(11, 5) to LocalTime.of(11, 50),  // 4
        LocalTime.of(13, 30) to LocalTime.of(14, 15), // 5
        LocalTime.of(14, 25) to LocalTime.of(15, 10), // 6
        LocalTime.of(15, 30) to LocalTime.of(16, 15), // 7
        LocalTime.of(16, 25) to LocalTime.of(17, 10), // 8
        LocalTime.of(18, 30) to LocalTime.of(19, 15), // 9
        LocalTime.of(19, 25) to LocalTime.of(20, 10), // 10
        LocalTime.of(20, 20) to LocalTime.of(21, 5),  // 11
    )

    /** 内置浏览器从门户登录页开始（教务系统的深链在未过门户时打不开）。 */
    override fun devBrowserStartUrl(school: School): String = UjsWebVpnFlow.entryUrl()

    /** 三台主机的会话 Cookie 名字审计（重置/退出登录后确认已回到未登录）。 */
    override fun cookieAuditOrigins(school: School): List<String> = listOf(
        "https://webvpn.ujs.edu.cn", // WebVPN 门户会话
        "https://pass.ujs.edu.cn",   // 统一身份认证（CAS）会话
        "https://jwxt.ujs.edu.cn",   // 教务系统会话
    )

    /** 设置页「通过 WebVPN」开关（勾选 = 链路 A，未勾选 = 链路 B）。 */
    override val schoolToggle: SchoolToggleSpec = SchoolToggleSpec(
        id = "ujs_webvpn",
        valueOf = { it.ujsWebVpn },
        set = { ctx, enabled -> SettingsStore.setUjsWebVpn(ctx, enabled) },
        title = { it.labelViaWebVpn },
        subtitle = { it.labelViaWebVpnSub },
    )

    // =======================================================================
    // 手动登录后的抓取：两条链路（「通过 WebVPN」勾选与否）
    //
    // 链路 A / 链路 B 的入口地址、课表地址与回退候选分别由 data/UjsFlow.kt 的
    // [UjsWebVpnFlow] / [UjsDefaultFlow] 独立持有。下面两份实现**刻意不共用任何
    // 流程代码**：导航、等待、提取、错误文案各写一份，改动任一方都不牵连另一方
    // （用户的明确要求）。因此本文件里成对出现、内容相似的函数是**有意为之**，
    // 不是可以合并的重复代码。
    //
    // 真正共享的只有与本链路无关的通用设施：页面探针（host.probePage / host.waitFor）、
    // 课程块计数（host.countCourseBlocks）与提取（host.customExtract，内部已含补充课程合并）。
    // =======================================================================

    /**
     * 手动登录学校的同步分派。
     *
     * 【只在这一处做判定】按勾选状态选择链路 A 或 B，之后两条链路各自走完全程，
     * 中间不再交叉。前置条件：用户已在可见的网页窗口完成登录（拖滑块 / 短信码），
     * 会话 Cookie 留在全进程共享的 CookieManager 中。两份实现都**不做任何登录尝试**：
     * 不填表、不猜登录地址、不做兜底跳转——页面状态不明时一律交给用户。
     */
    override suspend fun syncManually(host: ManualSyncHost, view: WebView): SyncResult? {
        val useWebVpn = SettingsStore.read(host.appContext).ujsWebVpn
        AppLog.i(TAG, "手动登录：链路 = " + if (useWebVpn) "A·通过 WebVPN" else "B·默认方式")
        return if (useWebVpn) syncWebVpn(host, view) else syncDefault(host, view)
    }

    // ------------------------------------------------------ 链路 A：通过 WebVPN

    /** 勾选「通过 WebVPN」时的同步实现（地址取自 [UjsWebVpnFlow]）。 */
    private suspend fun syncWebVpn(host: ManualSyncHost, view: WebView): SyncResult {
        // 1) 当前页面已渲染出课程块（用户自己已经点到了课表页）
        val blocks = host.countCourseBlocks(view)
        if (blocks > 0) {
            AppLog.i(TAG, "链路A：当前页面已渲染课表（$blocks 个课程块），直接提取")
            return extractWebVpn(host, view)
        }

        // 2) 无条件导航到课表页。CookieManager 全进程共享：用户已在网页窗口登录过的话
        //    会话直接生效；教务会话过期但 CAS 会话还在时，jwxt 会自动 302 到 CAS 带
        //    ticket 回跳，同样无需人工。
        //    【为什么不能按「当前页面是否已过门户」决定跳不跳】引擎 WebView 可能停在
        //    上次同步残留的 CAS 登录页——它在 WebVPN 代理下也算「已过门户」，但页面
        //    本身带登录表单，会被误判成「未登录」（实测：同步耗时 0.03s 直接失败）。
        //    所以这里无条件导航，以落定页面为准。
        AppLog.i(TAG, "链路A 阶段②：通过 WebVPN 访问教务系统 → 打开课表页")
        val probe = navigateWebVpn(host, view)
        if (probe == null || probe.notFound) {
            return SyncResult.Failure(
                "${host.school.name} 课表页暂时打不开（404），请稍后重试；" +
                    "若持续出现请把日志发给开发者"
            )
        }

        // 3) 会话状态判定。
        //    只有落在 jwxt / xuanke 这两台**教务主机**上才算「已在教务系统内」；
        //    仅凭「地址里有 /http/」不够——统一身份认证页也在 /https/ 代理下，会被误判。
        if (!UjsWebVpnFlow.isCampusUrl(probe.url)) {
            AppLog.w(TAG, "链路A：尚不在教务主机上（${probe.url.take(80)}），引导用户手动登录")
            return SyncResult.LoginRequired(LOGIN_GUIDE)
        }
        // CAS 回跳有时在导航等待超时后才完成：再多等一拍
        var settled: PageProbe = probe
        if (settled.hasLoginForm && isCasPage(settled.url)) {
            val later = host.waitFor(view, 8_000L) { it.hasGrid || it.hasApp }
            settled = later?.takeIf { it.hasGrid || it.hasApp } ?: host.probePage(view) ?: settled
            AppLog.i(TAG, "链路A：CAS 回跳等待后 ${settled.summary()}")
        }
        if (settled.hasLoginForm) {
            AppLog.w(TAG, "链路A：教务系统未登录（课表页出现登录表单）")
            return SyncResult.LoginRequired(SCHEDULE_LOGIN_GUIDE)
        }
        val deadline = SystemClock.uptimeMillis() + SCHEDULE_RENDER_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            delay(500)
            if (host.countCourseBlocks(view) > 0) break
        }
        AppLog.i(TAG, "链路A 阶段③：课表页已就绪（${host.countCourseBlocks(view)} 个课程块），开始提取")
        return extractWebVpn(host, view)
    }

    /**
     * 链路 A 的导航：按 [UjsWebVpnFlow.scheduleFallbacks] 的候选序列逐个尝试，
     * 页面 404 时自动回退到下一个变体（原样 → 加 /jwglxt 前缀 → 根路径兜底）。
     *
     * 【刻意与链路 B 各自成文】两份导航代码内容相似但不共用函数：
     * 以后只调 A 的回退顺序/等待时长，绝不会牵连 B。
     */
    private suspend fun navigateWebVpn(host: ManualSyncHost, view: WebView): PageProbe? {
        val targets = UjsWebVpnFlow.scheduleFallbacks()
        var last: PageProbe? = null
        for ((idx, cand) in targets.withIndex()) {
            if (idx > 0) AppLog.i(TAG, "链路A 404，尝试备用路径（${idx + 1}/${targets.size}）: ${cand.take(90)}")
            else AppLog.i(TAG, "链路A 导航: ${cand.take(90)}")
            val beforeUrl = host.probePage(view)?.url.orEmpty()
            view.loadUrl(cand)
            // 先等地址离开旧页（可能经历多次 302），再等内容信号（404/登录表单/课表/网络错误）。
            // CAS 登录页（/cas/…）自带登录表单，但它是「已有 CAS 会话时自动 ticket 回跳」
            // 链路的中间站，不能当终态信号，否则会在跳转完成前提前返回、把有效会话误判成未登录。
            host.waitFor(view, 6_000L) { it.url.isNotBlank() && it.url != beforeUrl }
            host.waitFor(view, 12_000L) {
                it.notFound || it.hasGrid || it.hasApp || it.netError ||
                    (it.hasLoginForm && !isCasPage(it.url))
            }
            last = host.probePage(view)
            AppLog.i(TAG, "  → ${last?.summary() ?: "无响应"}")
            if (last != null && !last.notFound && last.error.isBlank()) return last
        }
        return last
    }

    /**
     * 链路 A 的提取。正方课表页首次打开表格是空的：先点「查询」触发异步渲染，
     * 再等课程块出现，最后执行适配器脚本（补充课程由引擎在 customExtract 内并入）。
     */
    private suspend fun extractWebVpn(host: ManualSyncHost, view: WebView): SyncResult {
        if (host.countCourseBlocks(view) == 0) {
            AppLog.i(TAG, "链路A：课表未渲染，尝试触发「查询」")
            host.evaluateJs(view, JsScripts.UJS_TRIGGER_QUERY)
            val deadline = SystemClock.uptimeMillis() + QUERY_RENDER_WAIT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                delay(500)
                if (host.countCourseBlocks(view) > 0) break
            }
            AppLog.i(TAG, "链路A 查询后课程块数量: ${host.countCourseBlocks(view)}")
        }
        return host.customExtract(view, JsScripts.UJS_EXTRACT)
    }

    // ------------------------------------------------------ 链路 B：默认方式

    /** 未勾选「通过 WebVPN」时的同步实现（地址取自 [UjsDefaultFlow]）。 */
    private suspend fun syncDefault(host: ManualSyncHost, view: WebView): SyncResult {
        val blocks = host.countCourseBlocks(view)
        if (blocks > 0) {
            AppLog.i(TAG, "链路B：当前页面已渲染课表（$blocks 个课程块），直接提取")
            return extractWebVpnDefault(host, view)
        }
        AppLog.i(TAG, "链路B 阶段②：打开课表页")
        val probe = navigateDefault(host, view)
        if (probe == null || probe.notFound) {
            return SyncResult.Failure(
                "${host.school.name} 课表页暂时打不开（404），请稍后重试；" +
                    "若持续出现请把日志发给开发者"
            )
        }
        if (!UjsDefaultFlow.isCampusUrl(probe.url)) {
            AppLog.w(TAG, "链路B：尚不在教务主机上（${probe.url.take(80)}），引导用户手动登录")
            return SyncResult.LoginRequired(LOGIN_GUIDE)
        }
        var settled: PageProbe = probe
        if (settled.hasLoginForm && isCasPage(settled.url)) {
            val later = host.waitFor(view, 8_000L) { it.hasGrid || it.hasApp }
            settled = later?.takeIf { it.hasGrid || it.hasApp } ?: host.probePage(view) ?: settled
            AppLog.i(TAG, "链路B：CAS 回跳等待后 ${settled.summary()}")
        }
        if (settled.hasLoginForm) {
            AppLog.w(TAG, "链路B：教务系统未登录（课表页出现登录表单）")
            return SyncResult.LoginRequired(SCHEDULE_LOGIN_GUIDE)
        }
        val deadline = SystemClock.uptimeMillis() + SCHEDULE_RENDER_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            delay(500)
            if (host.countCourseBlocks(view) > 0) break
        }
        AppLog.i(TAG, "链路B 阶段③：课表页已就绪（${host.countCourseBlocks(view)} 个课程块），开始提取")
        return extractWebVpnDefault(host, view)
    }

    /** 链路 B 的导航：候选序列来自 [UjsDefaultFlow.scheduleFallbacks]（当前与链路 A 同址）。 */
    private suspend fun navigateDefault(host: ManualSyncHost, view: WebView): PageProbe? {
        val targets = UjsDefaultFlow.scheduleFallbacks()
        var last: PageProbe? = null
        for ((idx, cand) in targets.withIndex()) {
            if (idx > 0) AppLog.i(TAG, "链路B 404，尝试备用路径（${idx + 1}/${targets.size}）: ${cand.take(90)}")
            else AppLog.i(TAG, "链路B 导航: ${cand.take(90)}")
            val beforeUrl = host.probePage(view)?.url.orEmpty()
            view.loadUrl(cand)
            host.waitFor(view, 6_000L) { it.url.isNotBlank() && it.url != beforeUrl }
            host.waitFor(view, 12_000L) {
                it.notFound || it.hasGrid || it.hasApp || it.netError ||
                    (it.hasLoginForm && !isCasPage(it.url))
            }
            last = host.probePage(view)
            AppLog.i(TAG, "  → ${last?.summary() ?: "无响应"}")
            if (last != null && !last.notFound && last.error.isBlank()) return last
        }
        return last
    }

    /** 链路 B 的提取（与链路 A 各自成文，不互相调用）。 */
    private suspend fun extractWebVpnDefault(host: ManualSyncHost, view: WebView): SyncResult {
        if (host.countCourseBlocks(view) == 0) {
            AppLog.i(TAG, "链路B：课表未渲染，尝试触发「查询」")
            host.evaluateJs(view, JsScripts.UJS_TRIGGER_QUERY)
            val deadline = SystemClock.uptimeMillis() + QUERY_RENDER_WAIT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                delay(500)
                if (host.countCourseBlocks(view) > 0) break
            }
            AppLog.i(TAG, "链路B 查询后课程块数量: ${host.countCourseBlocks(view)}")
        }
        return host.customExtract(view, JsScripts.UJS_EXTRACT)
    }

    /**
     * 地址是否是统一身份认证（CAS）页面。
     *
     * CAS 登录页自带登录表单，但「已有 CAS 会话」访问它会自动 302 带 ticket 回跳
     * 教务系统——它是回跳链的中间站而非终点。判定「未登录」时必须把这类页面
     * 与教务系统自身的登录页（如 `/xtgl/login_slogin.html`）区分开。
     */
    private fun isCasPage(url: String): Boolean =
        url.contains("/cas/", ignoreCase = true) || url.contains("authserver", ignoreCase = true)

    private const val LOGIN_GUIDE =
        "请先完成登录：返回「设置」→ 点「网页登录」，按页面提示依次完成" +
            "① WebVPN 门户登录 ② 教务系统登录，看到课表后再回来同步"

    private const val SCHEDULE_LOGIN_GUIDE =
        "教务系统还没登录：请点「网页登录」，在打开的页面里点「登录」完成一卡通登录" +
            "（会自动把你带到登录入口），看到课表后再回来点同步"
}
