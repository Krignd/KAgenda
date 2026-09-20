package com.kstudio.agenda.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

/** 同步结果 */
sealed interface SyncResult {
    data class Success(
        val semester: SemesterSchedule,
        val rawCaptureCount: Int,
        /** 非空时提示需要用户留意的信息（如：新密码未能验证） */
        val notice: String = "",
    ) : SyncResult

    data class LoginRequired(val message: String) : SyncResult
    data class Failure(val message: String, val cause: Throwable? = null) : SyncResult
}

/** WebView → Android 的数据桥（捕获到的接口 JSON） */
class KebiaoBridge(private val sink: (url: String, body: String) -> Unit) {
    @JavascriptInterface
    fun onApiData(url: String, body: String) {
        try {
            sink(url, body)
        } catch (_: Throwable) {
        }
    }
}

/**
 * 课表抓取引擎（无界面 WebView + JS 注入）。
 *
 * 同步流程（每一步都记录日志）：
 * 1. 先直接打开教务系统首页（已有会话时立即出课表）；
 * 2. 若出现“网络异常”页 / 登录表单 / 无课表 → 打开统一身份认证
 *    （https://sso.buaa.edu.cn/login），自动填写学号密码并提交（支持 iframe 内表单）；
 * 3. 登录成功后重新打开首页，等待“我的课表”周网格渲染完成；
 * 4. 优先解析挂钩捕获的 getMyScheduleDetail 接口响应（结构化、字段最全），
 *    失败时回退解析 DOM 提取结果；
 * 5. 全程超时保护（单次 JS 调用 5 秒 + 各阶段超时），不会再出现一直“正在同步”。
 */
class WebScheduleEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "Engine"
        const val HOME_URL = "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/home/index.html#/"
        const val SSO_LOGIN_URL = "https://sso.buaa.edu.cn/login"

        /**
         * service 参数（关键）：
         * 教务系统的**接口**启用了 CAS 过滤——未登录时接口会 302 到 sso?service=<接口地址>。
         * 因此 service 用接口地址，CAS 跳回时由后端验证 ticket 并种下会话 Cookie，
         * 之后打开首页即可正常加载课表。
         */
        const val SERVICE_API =
            "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/api/home/currentUser.do"

        /** 首页地址（作为 service 的备用值） */
        const val SERVICE_HOME =
            "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/home/index.html"

        /**
         * 带 service 参数的统一认证地址。
         * 不带 service 访问 /login，登录成功后 CAS 会跳到默认应用（个人中心 uc.buaa.edu.cn）；
         * 带 service 后：已有会话时 CAS 直接带 ticket 跳到 service 地址（由后端完成会话建立），
         * 需要登录时也会在登录成功后跳回 service 地址。
         */
        fun ssoUrlWithService(service: String = SERVICE_API): String =
            SSO_LOGIN_URL + "?service=" + android.net.Uri.encode(service)

        private const val POLL_INTERVAL_MS = 400L        // 页面探针间隔（提速：原 1.5 秒 → 0.4 秒）
        private const val WEEKS_POLL_INTERVAL_MS = 300L  // 整学期重放结果轮询间隔
        private const val JS_TIMEOUT_MS = 5_000L
        private const val AUTOFILL_ATTEMPTS = 2
        private const val WEEKS_FETCH_TIMEOUT_MS = 60_000L   // 整学期按周重放的等待时间

        /** 隐藏同步 WebView 需要拦截的图片请求（提速；不影响手动网页登录窗口） */
        private val IMAGE_URL_REGEX = Regex("\\.(png|jpe?g|gif|webp)(\\?|$)", RegexOption.IGNORE_CASE)

        private const val HOME_FIRST_WAIT_MS = 20_000L    // 打开首页后等待课表的时间
        private const val LOGIN_PAGE_WAIT_MS = 25_000L    // 等待登录表单出现的时间
        private const val SUBMIT_JUMP_WAIT_MS = 15_000L   // 提交后等待 CAS 跳转的时间
        private const val HOME_RETRY_WAIT_MS = 45_000L    // 登录后等待课表的时间

        /** 账密被明确拒绝时的统一提示（此时重试同一份凭据无意义，立即终止登录流程） */
        private const val MSG_CREDENTIAL_ERROR =
            "登录失败：账号或密码错误，请核对「设置」中的学号与密码后重试"

        /** 用户手动登录时，若旧会话仍有效导致新密码无法验证，给出的中性提示 */
        private const val MSG_CREDENTIAL_UNVERIFIED =
            "已同步（未能校验新密码：当前登录会话仍然有效，可在设置中「退出登录」后再登录）"

        /** 统一认证常见的会话 Cookie 名（手动登录前清除，以强制出现登录表单） */
        private val CAS_COOKIE_NAMES = listOf(
            "CASTGC", "TGC", "CASTGC_JSESSIONID", "JSESSIONID", "SESSION", "SESSIONID", "CASPRIVACY",
        )
    }

    private var webView: WebView? = null

    /** 当前学校（同步开始时按设置刷新；默认北航） */
    private var school: School = Schools.of(null)

    /** 拼统一认证地址（按当前学校） */
    private fun ssoUrlWith(service: String): String =
        school.ssoUrl + "?service=" + android.net.Uri.encode(service)

    private val apiCaptures = ConcurrentLinkedQueue<Pair<String, String>>()
    private var lastStateKey = ""

    /** 主页导航完成计数（onPageFinished 自增），用于等待“刷新后的新文档”就绪 */
    private var pageReadyCount = 0

    /** 由 Activity 提供宿主 WebView（可见上下文更稳定）；重复调用会替换旧的实例 */
    fun attach(view: WebView) {
        if (webView === view) return
        configure(view)
        webView = view
    }

    /**
     * 供界面宿主获取共享的隐藏 WebView：
     * - 已创建过则复用同一实例（不会重复创建）；
     * - 若仍挂在旧父容器上会先摘除，便于重新挂载到当前窗口。
     * 注意：WebView 首次创建会阻塞主线程数百毫秒，界面侧应在需要时才调用（如同步进行中）。
     */
    fun obtainHostView(): WebView {
        val view = ensureWebView()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        return view
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // SPA 路由/状态依赖 localStorage
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(
            KebiaoBridge { url, body ->
                if (apiCaptures.size < 25) {
                    apiCaptures.add(url to body)
                    AppLog.d(TAG, "捕获接口: ${shortUrl(url)} (${body.length} 字符)")
                }
            },
            "KebiaoBridge"
        )
        // 页面生命周期与加载错误写入日志
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                AppLog.d(TAG, "加载开始: ${shortUrl(url)}")
                v.evaluateJavascript(JsScripts.HOOKS, null)
            }

            override fun onPageFinished(v: WebView, url: String?) {
                pageReadyCount++
                AppLog.d(TAG, "加载完成: ${shortUrl(url)}")
                v.evaluateJavascript(JsScripts.HOOKS, null)
            }

            override fun onReceivedError(
                v: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    AppLog.w(
                        TAG,
                        "加载错误: ${error.errorCode} ${error.description} @ ${shortUrl(request.url.toString())}"
                    )
                }
            }

            override fun shouldInterceptRequest(
                v: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val u = request.url?.toString().orEmpty()
                // 跳过被墙/慢速的外部 CDN（会阻塞登录页渲染，页面自带本地 vant.css）
                if (u.contains("cdn.jsdelivr.net")) {
                    return WebResourceResponse(
                        "text/css",
                        "utf-8",
                        java.io.ByteArrayInputStream(ByteArray(0)),
                    )
                }
                // 提速：同步用的 WebView 是隐藏的、也无需图片；直接拦掉常见图片请求，减少等待
                // （网页版登录/手动操作走的是 WebLoginActivity 的独立 WebView，不受影响）
                if (IMAGE_URL_REGEX.containsMatchIn(u)) {
                    return WebResourceResponse(
                        "image/png",
                        null,
                        java.io.ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return null
            }
        }

        // JS 控制台消息是 WebChromeClient 的回调（WebViewClient 没有该方法）
        view.webChromeClient = object : WebChromeClient() {

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                AppLog.d(TAG, "JS控制台: ${consoleMessage.message()} @${consoleMessage.lineNumber()}")
                return true
            }
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val created = WebView(appContext)
        configure(created)
        webView = created
        return created
    }

    /** 页面状态探针结果 */
    private data class PageProbe(
        val url: String = "",
        val hasLoginForm: Boolean = false,
        val captcha: Boolean = false,
        val hasGrid: Boolean = false,
        val hasApp: Boolean = false,
        val netError: Boolean = false,
        val ssoError: Boolean = false,
        val error: String = "",
        val snippet: String = "",
    ) {
        fun summary(): String = buildString {
            append("login=$hasLoginForm captcha=$captcha grid=$hasGrid app=$hasApp netError=$netError")
            if (ssoError) append(" ssoError=true")
            if (error.isNotBlank()) append(" error=").append(error)
            append(" url=").append(url.removePrefix("https://").take(90))
        }
    }

    /**
     * 自定义适配：执行学校适配器提供的 extractJs，把其返回的 JSON 解析为课表。
     * 适配器返回格式：[{title,day,start,end,room,teacher,weeks?},…]
     * 或 {"weekNo": N, "courses":[…]}（weekNo 为当前教学周，缺省 1）。
     * 兼容异步渲染：可配置 waitSelector 等待关键元素；数据为空时自动重试几次。
     */
    private suspend fun customExtract(view: WebView, script: String): SyncResult {
        AppLog.i(TAG, "自定义适配：执行 extractJs（${school.name}）")
        // 可选：等待适配器指定的就绪选择器出现（异步渲染的课表页）
        school.waitSelector?.let { sel ->
            val quoted = JSONObject.quote(sel)
            val probeZs = "(function(){try{return String(!!document.querySelector($quoted))}catch(e){return 'false'}})()"
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (SystemClock.uptimeMillis() < deadline) {
                if (evaluateJs(view, probeZs) == "true") break
                delay(500)
            }
            AppLog.i(TAG, "等待选择器结束: $sel")
        }
        // 页面数据可能异步加载：空结果时重试
        var raw: String? = null
        for (attempt in 1..6) {
            raw = evaluateJs(view, script)
            if (!raw.isNullOrBlank()) break
            delay(1_000)
        }
        if (raw.isNullOrBlank()) return SyncResult.Failure("适配代码未返回数据（请检查 extractJs / waitSelector）")
        val sem = ScheduleParser.fromCustomAdapter(raw, java.time.LocalDate.now())
            ?: return SyncResult.Failure("适配代码返回的数据无法解析为课表")
        AppLog.i(TAG, "自定义适配成功：${sem.weekNumbers}")
        return SyncResult.Success(sem, 1)
    }

    /**
     * 页面是否已具备提取条件：
     * - 内置判定：出现课表网格或应用主体且无网络异常；
     * - 自定义学校：页面已落在教务主机且未检测到登录表单/未认证授权，
     *   具体就绪与否交给适配器的 probeJs / extractJs 判定。
     */
    private fun looksReady(p: PageProbe?): Boolean {
        if (p == null || p.netError) return false
        if (p.hasGrid || p.hasApp) return true
        return school.custom && !p.hasLoginForm && !p.ssoError &&
            hostOf(p.url).isNotBlank() && hostOf(p.url).endsWith(school.host)
    }

    /** 登录页反馈是否为「账号或密码错误」类提示（该情况下重试同一凭据无意义，应立即终止） */
    private fun isCredentialError(text: String): Boolean =
        text.contains("凭据错误") || text.contains("密码错误") ||
            text.contains("用户名或密码") || text.contains("账号或密码") ||
            text.contains("密码不正确") || text.contains("密码有误") ||
            text.contains("invalid", ignoreCase = true) || text.contains("incorrect", ignoreCase = true)

    /**
     * 清除统一身份认证的会话 Cookie（仅认证主机），使登录页重新出现表单。
     * 用于「用户手动登录」场景：否则已有 CAS 会话会让错误密码“看起来登录成功”。
     * 教务系统的会话 Cookie 不动：万一新密码有误，已缓存的课表仍可离线查看。
     */
    private fun clearCasSession() {
        val host = hostOf(school.ssoUrl)
        if (host.isBlank()) return
        val cm = CookieManager.getInstance()
        val url = "https://$host"
        CAS_COOKIE_NAMES.forEach { name ->
            listOf("/", "/cas", "/login").forEach { path ->
                runCatching { cm.setCookie(url, "$name=; Path=$path; Max-Age=0") }
            }
        }
        runCatching { cm.flush() }
        AppLog.i(TAG, "已清除统一认证会话 Cookie（强制重新登录以校验新密码）")
    }

    /** Success 附加提示（其他结果原样返回） */
    private fun SyncResult.withNotice(notice: String): SyncResult =
        if (this is SyncResult.Success && notice.isNotBlank()) copy(notice = notice) else this

    /**
     * 执行一次同步（阶段化流程，全程带日志与超时保护）。
     * 必须在主线程调用（内部会自行切换）。
     * @param incrementalAgainstSemester 非空时启用增量模式：若页面解析出的学期与该值（缓存的学期标签）
     *   一致，则跳过整学期按周重放，仅抓当前周 DOM（保存时由仓库与旧缓存合并）。用于静默自动刷新，
     *   可省去十几次接口请求；学期不一致时会自动降级为完整抓取。
     * @param verifyCredentials 用户在设置页手动登录时为 true：先清除统一认证会话，强制走一次真实
     *   登录校验新密码（否则已有会话会让错误密码“看起来登录成功”）。
     */
    suspend fun fetchWeek(
        credentials: Credentials?,
        timeoutMillis: Long = 210_000L,
        incrementalAgainstSemester: String? = null,
        verifyCredentials: Boolean = false,
    ): SyncResult = withContext(Dispatchers.Main) {
        val startAt = SystemClock.uptimeMillis()
        val hardDeadline = startAt + timeoutMillis
        try {
            apiCaptures.clear()
            lastStateKey = ""
            // 注册密码为敏感词：即使页面控制台/异常信息意外包含密码，也不会被写进日志文件
            credentials?.let { AppLog.registerSecret(it.password) }
            // 按「设置 → 学校」确定当前学校；未适配学校给出明确提示
            school = Schools.of(SettingsStore.read(appContext).schoolId)
            if (!school.supported) {
                return@withContext SyncResult.Failure(
                    "${school.name} 的课表同步暂不可用，可在「设置 → 学校」切换其他学校"
                )
            }
            val view = ensureWebView()
            AppLog.i(TAG, "===== 开始同步（凭据：${if (credentials != null) "已保存" else "未保存"}${if (verifyCredentials) "，校验新密码" else ""}）=====")
            // 用户在设置页手动登录：先清掉统一认证会话，保证下面的登录流程确实用了新密码
            if (verifyCredentials && credentials != null) clearCasSession()

            // ---------- 阶段 1：直接打开教务系统首页（已有会话时最快） ----------
            // 若 WebView 已停留在首页，loadUrl 同一地址可能不会真正刷新（沿用上一轮残留的 DOM），
            // 会导致退出登录后仍被误判为“已登录”。这里强制真正重新加载，并等待新文档就绪。
            val readyBefore = pageReadyCount
            val currentUrl = view.url.orEmpty()
            val schoolHome = school.homeUrl.substringBefore("#")
            if (schoolHome.isNotBlank() && currentUrl.startsWith(schoolHome)) {
                AppLog.i(TAG, "WebView 已在首页，强制刷新以核对最新会话状态")
                view.reload()
            } else {
                view.loadUrl(school.homeUrl)
            }
            // 等待本次导航完成（onPageFinished），避免随后探测到刷新前的残留旧 DOM（最多等 10 秒）
            val readyDeadline = SystemClock.uptimeMillis() + 10_000
            while (pageReadyCount <= readyBefore && SystemClock.uptimeMillis() < readyDeadline) {
                delay(200)
            }
            var probe = waitFor(view, HOME_FIRST_WAIT_MS) { p -> looksReady(p) || p.hasLoginForm || p.netError }
            AppLog.i(TAG, "阶段1 首页探测: ${probe?.summary() ?: "超时无响应"}")

            // 只有“已渲染课表/应用且无网络异常”才可直接提取；
            // “网络异常 + 残留旧课表”说明会话已失效但页面还留着旧内容（例如退出登录后），
            // 必须转入登录流程重新建立会话，否则按周重放接口会全部失败。
            if (probe?.netError == true && (probe.hasGrid || probe.hasApp)) {
                AppLog.w(TAG, "首页显示网络异常但残留旧课表内容，判定会话已失效，转入登录流程")
            }
            if (looksReady(probe)) {
                if (!verifyCredentials) {
                    return@withContext extract(view, "首页直出", incrementalAgainstSemester)
                }
                // 手动登录：即使首页已有会话也继续走登录流程，确保新密码被真正提交校验
                AppLog.i(TAG, "手动登录：首页已有会话，仍继续走登录流程校验新密码")
            }

            // ---------- 阶段 2：走统一身份认证 ----------
            // 注意：即使没有保存学号密码，也先利用“已有 CAS 会话”尝试回跳建立教务系统会话
            // （网页登录成功后的场景：本地没存密码，但 Cookie 会话有效）
            var loginProbe: PageProbe?
            if (probe?.hasLoginForm == true) {
                loginProbe = probe
                AppLog.i(TAG, "首页已跳转登录页")
            } else {
                loginProbe = openSsoAndWait(view)
            }

            if (looksReady(loginProbe)) {
                // 手动登录却直接回到了课表（旧会话仍未失效）→ 无法校验新密码，如实告知用户
                val notice = if (verifyCredentials) MSG_CREDENTIAL_UNVERIFIED else ""
                if (notice.isNotBlank()) AppLog.w(TAG, "未能校验新密码：统一认证仍处于登录态")
                return@withContext extract(view, "登录页直达课表", incrementalAgainstSemester)
                    .withNotice(notice)
            }
            if (loginProbe == null || !loginProbe.hasLoginForm) {
                val p = loginProbe
                return@withContext SyncResult.Failure(
                    "无法打开登录页面：${p?.summary() ?: "页面无响应（请检查网络）"}"
                )
            }
            if (credentials == null) {
                AppLog.w(TAG, "需要输入密码但未保存凭据（网页会话可能已过期）")
                // 【已隐藏保留】原文案提示使用「网页登录」；入口隐藏后仅引导账密登录
                return@withContext SyncResult.LoginRequired(
                    "登录会话已过期：请在「设置」页填写学号密码后重试"
                )
            }
            if (loginProbe.captcha) {
                AppLog.w(TAG, "登录页出现验证码，暂时无法自动处理")
                // 【已隐藏保留】原文案提示使用「网页登录」手动完成；入口隐藏后给出中性提示
                return@withContext SyncResult.LoginRequired("登录需要验证码，暂时无法自动完成，请稍后重试")
            }

            // ---------- 阶段 3：自动填写并提交（每轮提交后都回教务系统验证会话） ----------
            var attempts = 0
            var lastError = ""
            while (attempts < AUTOFILL_ATTEMPTS && SystemClock.uptimeMillis() < hardDeadline) {
                attempts++
                val fillScript = school.loginJs?.let {
                    JsScripts.customFill(it, credentials.studentId, credentials.password)
                } ?: JsScripts.autoFill(credentials.studentId, credentials.password)
                val fillResult = evaluateJs(view, fillScript)
                AppLog.i(TAG, "自动填写#$attempts: ${fillResult ?: "无返回"}")

                // 等待 CAS 跳转：离开登录主机 / 出现表单校验错误 / 课表就绪
                val ssoHost = hostOf(school.ssoUrl)
                val jumped = waitFor(view, SUBMIT_JUMP_WAIT_MS) { p ->
                    val h = hostOf(p.url)
                    looksReady(p) || p.error.isNotBlank() ||
                        (h.isNotBlank() && (ssoHost.isBlank() || !h.endsWith(ssoHost)))
                }
                AppLog.i(TAG, "提交后状态: ${jumped?.summary() ?: "超时无响应"}")

                if (jumped != null && jumped.hasLoginForm && jumped.error.isNotBlank()) {
                    lastError = jumped.error
                    AppLog.w(TAG, "登录表单校验失败：$lastError")
                    if (lastError.contains("验证码")) {
                        // 【已隐藏保留】原提示使用「网页登录」；入口隐藏后改为中性提示
                        return@withContext SyncResult.LoginRequired(
                            "登录需要验证码，暂时无法自动完成，请稍后重试"
                        )
                    }
                    if (isCredentialError(lastError)) {
                        // 账密被明确拒绝：重试同一份凭据无法成功，且连续尝试可能触发学校风控/锁定 → 立即终止
                        AppLog.w(TAG, "账号或密码错误，终止登录重试")
                        return@withContext SyncResult.LoginRequired(MSG_CREDENTIAL_ERROR)
                    }
                    continue
                }

                // 关键：无论 CAS 落在哪个页面，都直接打开教务系统首页验证会话是否已建立
                // （byxt 的接口未登录时会自动 302 到 sso?service=...，已登录则直接返回数据）
                view.loadUrl(school.homeUrl)
                val homeProbe = waitFor(view, HOME_RETRY_WAIT_MS) { p ->
                    looksReady(p) || p.hasLoginForm || p.netError
                }
                AppLog.i(TAG, "回首页验证#$attempts: ${homeProbe?.summary() ?: "超时无响应"}")

                if (looksReady(homeProbe)) {
                    return@withContext extract(view, "登录后首页", incrementalAgainstSemester)
                }
                if (homeProbe?.hasLoginForm == true) {
                    if (homeProbe.error.isNotBlank()) {
                        lastError = homeProbe.error
                        AppLog.w(TAG, "登录失败：$lastError")
                        if (lastError.contains("验证码")) {
                            return@withContext SyncResult.LoginRequired(
                                "登录需要验证码，暂时无法自动完成，请稍后重试"
                            )
                        }
                        if (isCredentialError(lastError)) {
                            AppLog.w(TAG, "账号或密码错误，终止登录重试")
                            return@withContext SyncResult.LoginRequired(MSG_CREDENTIAL_ERROR)
                        }
                    }
                    // 会话未建立：此时停留在 SSO 登录页，进入下一轮自动填写
                    AppLog.w(TAG, "会话未建立，准备重试（已尝试 $attempts 次）")
                    continue
                }
                // 注：不再用首页文本快照覆盖错误信息——教务系统 SPA 在会话失效时会渲染
                // “网络异常”字样，用它当失败原因会误导用户（真实原因通常在表单校验分支）
            }

            if (lastError.isNotBlank()) {
                return@withContext SyncResult.LoginRequired("登录失败：$lastError")
            }
            // 【已隐藏保留】原文案提示使用「网页登录」；入口隐藏后改为引导检查账密
            return@withContext SyncResult.LoginRequired(
                "登录未完成（可能账号密码有误或需要验证码），请检查账号密码后重试"
            )
        } catch (t: Throwable) {
            AppLog.e(TAG, "同步异常", t)
            SyncResult.Failure("同步失败：${t.message ?: t.javaClass.simpleName}", t)
        } finally {
            runCatching { CookieManager.getInstance().flush() }
            val costSeconds = (SystemClock.uptimeMillis() - startAt) / 1000.0
            AppLog.i(TAG, "===== 同步流程结束（耗时 ${costSeconds} 秒）=====")
        }
    }

    /**
     * 打开统一认证页并等待其稳定状态（依次尝试不同 service 参数）：
     * 1. 带教务系统首页 service：已有 CAS 会话时直接带 ticket 跳回教务系统（最快路径）；
     * 2. service 被拒（未认证授权）时改用与教务系统接口一致的备用 service；
     * 3. 仍失败则退回普通登录页。
     */
    private suspend fun openSsoAndWait(view: WebView): PageProbe? {
        val candidates = listOf(
            ssoUrlWith(school.serviceApi),  // service = 接口地址（由后端验票并建立会话）
            ssoUrlWith(school.homeUrl),     // service = 首页地址
            school.ssoUrl,                  // 兜底：普通登录页
        )
        for ((index, url) in candidates.withIndex()) {
            AppLog.i(TAG, "打开统一身份认证#${index + 1}: ${url.take(150)}")
            view.loadUrl(url)
            val probe = waitFor(view, LOGIN_PAGE_WAIT_MS) { p ->
                p.hasLoginForm || p.hasGrid || p.hasApp ||
                    hostOf(p.url).endsWith(school.host) || p.ssoError
            }
            AppLog.i(TAG, "统一认证#${index + 1} 状态: ${probe?.summary() ?: "超时无响应"}")

            if (probe == null) continue
            if (probe.ssoError) {
                AppLog.w(TAG, "service 未被接受（未认证授权的服务），尝试下一个地址")
                continue
            }

            // 自定义学校：适配器自行处理登录页面差异；已在教务主机且未出现登录表单时，
            // 直接交给提取阶段（由 probeJs / extractJs 判定是否就绪）
            if (school.custom && !probe.hasLoginForm && hostOf(probe.url).endsWith(school.host)) {
                return probe
            }

            // 已被 CAS 带回教务系统（例如 service 指向接口地址）：打开首页等待课表渲染
            if (!probe.hasLoginForm && !probe.hasGrid && !probe.hasApp &&
                hostOf(probe.url).endsWith(school.host)
            ) {
                AppLog.i(TAG, "CAS 已回跳教务系统，打开首页等待课表")
                view.loadUrl(school.homeUrl)
                val back = waitFor(view, HOME_RETRY_WAIT_MS) { p ->
                    p.hasGrid || p.hasApp || p.hasLoginForm || p.netError
                }
                AppLog.i(TAG, "回跳后首页状态: ${back?.summary() ?: "超时无响应"}")
                if (looksReady(back)) return back
                if (back?.hasLoginForm == true) return back
                // 会话仍未建立 → 继续尝试下一个候选地址
                continue
            }
            return probe
        }
        return null
    }

    /** 从当前页面提取课表并解析（接口数据优先，DOM 兜底；再按周重放接口抓取整学期） */
    private suspend fun extract(
        view: WebView,
        phase: String,
        incrementalAgainstSemester: String? = null,
    ): SyncResult {
        // 自定义适配：学校注册了 extractJs 时，优先走适配器路径（返回值即课表 JSON）
        school.extractJs?.let { return customExtract(view, it) }

        // 增量模式（静默自动刷新）：先不启动整学期重放，待 DOM 解析出学期后再判定；
        // 学期与缓存一致则直接跳过（省去十几次接口请求），不一致时补做完整重放。
        val incremental = incrementalAgainstSemester != null
        if (!incremental) {
            // 提速：先立刻启动整学期重放（termCode 先由现有捕获推断，缺失时脚本自行获取），
            // 与下面的 DOM 解析并行执行，整体更快
            startWeeksFetch(view, ScheduleParser.inferTermParams(apiCaptures.map { it.second }))
        }

        val extracted = evaluateJs(view, JsScripts.EXTRACT)
        AppLog.i(TAG, "DOM 提取[$phase]: ${extracted?.length ?: 0} 字符")
        if (!extracted.isNullOrBlank()) {
            AppLog.d(TAG, "DOM 提取摘要: ${extracted.take(500)}")
        }

        // 等待页面渲染稳定（0.3 秒：留给未完成的接口捕获落地，尽量少等待）
        delay(300)

        val raws = apiCaptures.map { it.second }
        AppLog.i(TAG, "接口捕获数量: ${raws.size}")

        // 1) 解析当前周（接口捕获优先，DOM 兜底），得到周次/学期 meta。
        //    解析切到后台线程：接口捕获/DOM 文本可能达数 MB，在主线程解析会造成明显卡顿（ANR 风险）
        val currentWeek = withContext(Dispatchers.Default) {
            ScheduleParser.parseExtraction(extracted.orEmpty(), raws)
        }
        val meta = withContext(Dispatchers.Default) {
            ScheduleParser.readPageMeta(extracted.orEmpty())
        }
        val weekNo = currentWeek?.weekNo ?: meta?.weekNo ?: 1
        val weekRange = currentWeek?.weekRangeLabel ?: meta?.weekRange.orEmpty()
        val semesterLabel = currentWeek?.semesterLabel ?: meta?.semester.orEmpty()
        val fetchedAt = currentWeek?.fetchedAtMillis ?: System.currentTimeMillis()
        AppLog.i(
            TAG,
            "当前周解析: ${currentWeek?.courses?.size ?: 0} 条课程, 第${weekNo}周, " +
                "学期=$semesterLabel, 范围=$weekRange"
        )

        // 2) 获取整学期重放结果：
        //    - 完整模式：等待已在并行执行的整学期重放（见 startWeeksFetch / fetchWeeksScript）；
        //    - 增量模式且学期与缓存一致：跳过（仅保留当前周，保存时与缓存自动合并）；
        //    - 增量模式但条件不满足：补做完整重放。
        var weeks: Map<Int, List<Course>> = emptyMap()
        val canSkipReplay = incremental &&
            semesterLabel.isNotBlank() &&
            semesterLabel == incrementalAgainstSemester &&
            !currentWeek?.courses.isNullOrEmpty()
        if (canSkipReplay) {
            AppLog.i(TAG, "增量同步：学期与缓存一致（$semesterLabel），跳过整学期重放")
        } else {
            if (incremental) {
                AppLog.i(TAG, "增量条件不满足（学期=$semesterLabel），改为完整抓取")
                startWeeksFetch(view, ScheduleParser.inferTermParams(raws))
            }
            weeks = awaitWeeksResult(view)
        }

        val semester = withContext(Dispatchers.Default) {
            ScheduleParser.buildSemester(
                currentWeekCourses = currentWeek?.courses.orEmpty(),
                currentWeekNo = weekNo,
                weekRange = weekRange,
                semesterLabel = semesterLabel,
                fetchedAt = fetchedAt,
                weeks = weeks,
            )
        }
        if (semester == null) {
            return SyncResult.Failure("已进入课表页面但未解析到课程（可能页面改版），请把日志发给开发者")
        }
        // 原始接口抓包用于排查改版问题：写盘切到 IO 线程（原实现占用主线程）
        withContext(Dispatchers.IO) {
            apiCaptures.take(5).forEach { (url, body) -> ScheduleCache.saveRawCapture(appContext, url, body) }
        }
        AppLog.i(
            TAG,
            "解析成功: 共 ${semester.weeks.size} 周, 周次=${semester.weekNumbers}, " +
                "锚点(第1周周一)=${java.time.LocalDate.ofEpochDay(semester.anchorEpochDay)}, " +
                "本周一(第${weekNo}周)=${semester.mondayOf(weekNo.coerceAtLeast(1))}"
        )
        return SyncResult.Success(semester, raws.size)
    }

    /** 在页面上下文内启动“按周重放”异步抓取（结果写入 window.__kbWeeks，由 awaitWeeksResult 轮询） */
    private suspend fun startWeeksFetch(
        view: WebView,
        params: Pair<String, String>?,
    ) {
        val term = params?.first.orEmpty()
        val campus = params?.second.orEmpty()
        AppLog.i(TAG, "开始按周重放接口抓取整学期（termCode=${term.ifBlank { "(自动获取)" }}）")
        evaluateJs(view, JsScripts.fetchWeeksScript(term, campus))
    }

    /** 轮询整学期重放结果（页面内已并行分批执行，完成即返回） */
    private suspend fun awaitWeeksResult(view: WebView): Map<Int, List<Course>> {
        val deadline = SystemClock.uptimeMillis() + WEEKS_FETCH_TIMEOUT_MS
        var resultJson: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            delay(WEEKS_POLL_INTERVAL_MS)
            val raw = evaluateJs(view, JsScripts.FETCH_WEEKS_POLL) ?: continue
            try {
                val obj = JSONObject(raw)
                if (!obj.optBoolean("busy", false)) {
                    resultJson = obj.optString("data")
                    break
                }
            } catch (_: Throwable) {
            }
        }
        if (resultJson.isNullOrBlank()) {
            AppLog.w(TAG, "整学期抓取超时或未返回，回退为当前周")
            return emptyMap()
        }
        AppLog.d(TAG, "整学期抓取原始结果: ${resultJson.take(500)}")
        val map = ScheduleParser.parseSemesterFetchResult(resultJson)
        if (map.isEmpty()) {
            AppLog.w(TAG, "整学期抓取失败：未取到任何周次（多为会话失效导致接口被重定向），本次仅保留当前周，保存时将自动合并缓存")
        }
        AppLog.i(TAG, "整学期抓取完成: ${map.size} 周（${map.keys.sorted()}）")
        return map
    }

    /** 轮询页面状态直到满足条件或超时，返回最后一次探测结果 */
    private suspend fun waitFor(
        view: WebView,
        timeoutMs: Long,
        predicate: (PageProbe) -> Boolean,
    ): PageProbe? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var last: PageProbe? = null
        while (SystemClock.uptimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val probe = probePage(view) ?: continue
            last = probe
            logState(probe)
            if (predicate(probe)) return probe
        }
        return last
    }

    /** 状态变化时记日志（去重，避免刷屏） */
    private fun logState(probe: PageProbe) {
        val key = "${probe.url}|${probe.hasLoginForm}|${probe.captcha}|" +
            "${probe.hasGrid}|${probe.hasApp}|${probe.netError}|${probe.error}"
        if (key == lastStateKey) return
        lastStateKey = key
        AppLog.i(TAG, "页面状态: ${probe.summary()}")
    }

    /** 让网页重新加载（下拉刷新/手动刷新时调用） */
    suspend fun reload() = withContext(Dispatchers.Main) {
        runCatching { ensureWebView().reload() }
        Unit
    }

    private suspend fun probePage(view: WebView): PageProbe? {
        // 自定义学校可提供 probeJs 探针（不同教务系统的就绪/登录判定差异很大）
        val raw = evaluateJs(view, school.probeJs ?: JsScripts.DETECT) ?: return null
        return try {
            val json = JSONObject(raw)
            PageProbe(
                url = json.optString("url"),
                hasLoginForm = json.optBoolean("login", false),
                captcha = json.optBoolean("captcha", false),
                hasGrid = json.optBoolean("grid", false) || json.optBoolean("ready", false),
                hasApp = json.optBoolean("app", false),
                netError = json.optBoolean("netError", false),
                ssoError = json.optBoolean("ssoError", false),
                error = json.optString("error"),
                snippet = json.optString("snippet"),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 执行 JS 并返回字符串结果（脚本需自行 JSON.stringify）。
     * 单次调用 5 秒超时：即使回调不返回也不会卡住整个同步流程。
     */
    private suspend fun evaluateJs(view: WebView, script: String): String? =
        withTimeoutOrNull(JS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    view.evaluateJavascript(script) { value ->
                        if (cont.isActive) cont.resume(unwrapJsString(value))
                    }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    /** evaluateJavascript 回调结果是 JSON 字面量，这里还原为字符串 */
    private fun unwrapJsString(value: String?): String? {
        if (value == null || value == "null") return null
        return try {
            JSONTokener(value).nextValue() as? String
        } catch (_: Throwable) {
            value
        }
    }

    private fun shortUrl(url: String?): String =
        url?.removePrefix("https://")?.take(110) ?: "(空)"

    /** 解析 URL 主机名（不能用子串判断站点：SSO 的 service 参数里也含有 "homeapp"） */
    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
}
