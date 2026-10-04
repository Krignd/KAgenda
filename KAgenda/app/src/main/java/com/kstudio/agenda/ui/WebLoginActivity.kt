@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kstudio.agenda.data.JsScripts
import com.kstudio.agenda.data.KebiaoBridge
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.ui.theme.KAgendaTheme
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * 【已隐藏保留】可见的网页登录页（当前入口已按需求隐藏，代码保留未删除）
 * 入口开关：SettingsScreen.SHOW_WEB_LOGIN_ENTRY（false=隐藏）；恢复时改为 true 即可。
 *
 * - 先打开“会话检测接口”：未登录时服务器会自动 302 到统一身份认证登录页（避免“网络异常”白屏）；
 * - 若本地存有账号，会自动填充并提交（存在验证码时需要用户手动完成）；
 * - 检测到已进入“我的课表”后提示用户点击“完成”，返回后触发同步；
 * - 未登录时点“完成/返回”会先弹提示；全过程记录日志，便于排查。
 */
class WebLoginActivity : ComponentActivity() {

    companion object {
        /** 结果附带：是否检测到已成功进入课表页（true=登录成功） */
        const val EXTRA_WEB_LOGIN_OK = "web_login_ok"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i("WebLogin", "打开网页登录页")
        setContent {
            KAgendaTheme {
                WebLoginScreen(
                    onBack = { finish() },
                    onDone = { loggedIn ->
                        runCatching { CookieManager.getInstance().flush() }
                        AppLog.i("WebLogin", "用户点击完成（已检测到登录成功=$loggedIn），返回并触发同步")
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_WEB_LOGIN_OK, loggedIn))
                        finish()
                    },
                )
            }
        }
    }
}

internal data class LoginProbe(
    val url: String = "",
    val login: Boolean = false,
    val captcha: Boolean = false,
    val grid: Boolean = false,
    val app: Boolean = false,
    /** 页面已呈现“已登录后才会出现”的稳定特征（如正方课表页的查询按钮/课表容器） */
    val ready: Boolean = false,
    /**
     * 已通过 WebVPN 门户、进入被代理的教务系统。
     *
     * 门户放行后地址形如 `…/http/<hex>/…`；未登录时会被 302 到 `/login`。
     * 因此这是「登录确实成功了」的可靠标志（比课表是否渲染更早出现）。
     */
    val proxied: Boolean = false,
    /** 页面是 404（如 xuanke 上不存在的路径，Apache 返回「Object not found!」） */
    val notFound: Boolean = false,
    val netError: Boolean = false,
    val ssoError: Boolean = false,
    val error: String = "",
) {
    override fun toString(): String =
        "login=$login captcha=$captcha grid=$grid app=$app" +
            (if (ready) " ready=true" else "") +
            (if (proxied) " proxied=true" else "") +
            (if (notFound) " notFound=true" else "") +
            " netError=$netError" +
            (if (ssoError) " ssoError=true" else "") +
            " url=${url.take(80)}"
}

/** 统一认证外壳页的 iframe 兜底脚本：init.js 未设置 src 时补上（否则整页空白） */
private const val SSO_FRAME_FIX =
    "(function(){try{var f=document.getElementById('loginIframe');" +
        "if(f){var s=f.getAttribute('src')||'';if(!s||s.indexOf('about:')===0){" +
        "f.setAttribute('src','/cas/login-mobile.html');}}}catch(e){}})();"

@Composable
private fun WebLoginScreen(onBack: () -> Unit, onDone: (Boolean) -> Unit) {
    var homeReached by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("正在检测登录状态…") }
    /** 探针原始输出（调试用，显示在提示下方一行） */
    var probeText by remember { mutableStateOf("") }
    var lastProbe by remember { mutableStateOf<LoginProbe?>(null) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    // 当前学校（与「设置 → 学校」保持一致）；WebView 首屏地址、「去登录」跳转都按学校来。
    // SettingsStore.read 是挂起函数，先在协程里解析出 schoolId，再建 WebView。
    var schoolId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { schoolId = SettingsStore.read(context).schoolId }
    // 读设置是毫秒级的；未就绪时只渲一个空屏（不 return，保证下方所有
    // BackHandler / DisposableEffect 在每次重组中都按同一顺序被调用）
    val school = schoolId?.let { remember(it) { Schools.of(it) } }
    if (school == null) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {}
        return
    }
    // 该学校是否有专属登录流程（需要多跳引导的门户型学校）。
    // 有 → 整条自动流程与判定都交给它，通用流程完全不动；没有 → 走下面的通用流程。
    // 这里按学校数据/注册表决定，**不出现任何学校名字**，新增学校无需改本文件。
    val loginFlow = remember(school.id) { SchoolLoginFlows.of(school) }

    // 「已登录」判定：有专属流程的学校由它自己判定（条件更严格，见 SchoolLoginFlowUjs），
    // 其余学校用通用规则（课表已渲染即算已登录）。
    fun currentlyLoggedIn(): Boolean =
        loginFlow?.isLoggedIn(lastProbe, homeReached)
            ?: (homeReached || lastProbe?.let { it.grid || it.app || it.ready } == true)

    // 未登录时点击“返回/完成”先弹提示，避免用户在未完成登录时退出、导致课表无法同步
    val requestExit: (() -> Unit) -> Unit = { action ->
        if (currentlyLoggedIn()) action() else pendingExit = action
    }
    // 系统返回键同样走检测
    BackHandler {
        if (pendingExit != null) {
            pendingExit = null
        } else {
            requestExit(onBack)
        }
    }

    // 周期性探测页面状态并自动导航：
    //   教务首页“网络异常”/长时间无课表 → 去统一身份认证
    //   统一认证页 → 自动填写（最多 3 次）
    //   离开统一认证 → 回教务系统首页验证会话
    LaunchedEffect(webView, loginFlow) {
        val view = webView ?: return@LaunchedEffect
        // 有专属流程的学校（如门户 + 统一认证多跳的学校）：整条自动流程交给它，
        // 通用流程完全不动；这里只搭一个「改界面状态」的桥。
        loginFlow?.let { flow ->
            flow.run(object : LoginWindowHost {
                override val view: WebView get() = view
                override fun onProbe(probe: LoginProbe) {
                    lastProbe = probe
                    probeText = probe.toString()
                }

                override fun setStatus(text: String) {
                    statusText = text
                }

                override fun markLoggedIn() {
                    homeReached = true
                }
            })
            return@LaunchedEffect
        }
        val ssoHost = hostOf(school.ssoUrl)
        val schoolHost = school.host
        var lastAutoFillUrl = ""
        var autoFillCount = 0
        var homeStuckTicks = 0
        var lastNavTarget = ""
        var lastNavAt = 0L
        var ssoFallbackSteps = 0
        var lastProbeUrl = ""
        var ssoNoFormTicks = 0
        var firstTick = true

        fun navigate(target: String, reason: String) {
            val now = SystemClock.uptimeMillis()
            if (target == lastNavTarget && now - lastNavAt < 10_000L) return
            lastNavTarget = target
            lastNavAt = now
            AppLog.i("WebLogin", "自动跳转（$reason）: ${target.take(80)}")
            view.loadUrl(target)
        }

        while (true) {
            // 首次探测更快，尽快识别页面状态；之后每 2 秒一次
            delay(if (firstTick) 1_000 else 2_000)
            firstTick = false
            // 页面即将关闭（用户点了“完成”/“返回”）时停止探测与跳转，避免退出瞬间残留自动导航
            val act = view.context as? Activity
            if (act == null || act.isFinishing || act.isDestroyed) break
            val probe = probe(view, school) ?: continue
            statusText = probe.toString()
            lastProbe = probe
            AppLog.d("WebLogin", "状态: $probe")
            // URL 连续两次相同才算“稳定”，避免在 CAS 跳转中途抢占导航
            val urlStable = probe.url == lastProbeUrl
            lastProbeUrl = probe.url

            // 按主机名判断页面所属站点（不能用子串：SSO 地址的 service 参数里也含 "homeapp"）
            val host = hostOf(probe.url)
            val onSso = ssoHost.isNotBlank() && host.endsWith(ssoHost)
            val onSchool = schoolHost.isNotBlank() && host.endsWith(schoolHost)
            if (onSso && !probe.login) ssoNoFormTicks++ else ssoNoFormTicks = 0

            if (probe.grid || probe.app || probe.ready) {
                if (!homeReached) {
                    AppLog.i("WebLogin", "已进入课表页面，可以点“完成”了")
                }
                homeReached = true
                homeStuckTicks = 0
                continue
            }

            // 手动登录学校（江苏大学）在上面的分派里已经走完各自的链路并 return，
            // 不会到达这里；以下是通用（北航金智 jwapp 等）流程。
            when {
                onSso && probe.login && probe.captcha -> {
                    if (probe.url != lastAutoFillUrl) {
                        lastAutoFillUrl = probe.url
                        AppLog.i("WebLogin", "出现验证码，请手动输入验证码完成登录")
                    }
                }

                onSso && probe.login && probe.url != lastAutoFillUrl && autoFillCount < 3 -> {
                    lastAutoFillUrl = probe.url
                    autoFillCount++
                    val credentials = SettingsStore.credentials(context)
                    if (credentials != null) {
                        AppLog.registerSecret(credentials.password)
                        val script = JsScripts.autoFill(credentials.studentId, credentials.password)
                        view.evaluateJavascript(script, null)
                        AppLog.i("WebLogin", "自动填写登录表单（第 $autoFillCount 次）")
                    } else {
                        AppLog.i("WebLogin", "未保存账号，请在网页中输入学号密码")
                    }
                }

                // service 未被 CAS 接受 → 依次退化为备用 service、普通登录页
                onSso && probe.ssoError && ssoFallbackSteps < 2 -> {
                    ssoFallbackSteps++
                    val target = if (ssoFallbackSteps == 1) {
                        WebScheduleEngine.ssoUrlWithService(WebScheduleEngine.SERVICE_HOME)
                    } else {
                        WebScheduleEngine.SSO_LOGIN_URL
                    }
                    navigate(target, "service 被拒，切换登录入口#$ssoFallbackSteps")
                }

                // 已离开登录表单（登录成功跳转 / 落到个人中心等）→ 回教务首页验证会话；
                // 需连续多拍确认（页面可能在渲染表单/慢速加载），避免过早抢占导航
                onSso && !probe.login && urlStable && ssoNoFormTicks >= 3 ->
                    navigate(WebScheduleEngine.HOME_URL, "离开登录页回首页")

                !onSso && !onSchool && urlStable ->
                    navigate(WebScheduleEngine.HOME_URL, "回到教务首页")

                // CAS 带 ticket 跳回接口地址（后端已验证 ticket 并种下会话）→ 打开首页等待课表
                onSchool && probe.url.contains("/api/") ->
                    navigate(WebScheduleEngine.HOME_URL, "ticket 已受理，打开首页")

                // 教务首页出现“网络异常”或长时间没有课表 → 去统一认证（带 service，登录后自动跳回）
                onSchool && (probe.netError || homeStuckTicks >= 6) -> {
                    homeStuckTicks = 0
                    navigate(WebScheduleEngine.ssoUrlWithService(), "首页未登录，去统一认证")
                }

                onSchool -> homeStuckTicks++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        loginFlow?.title(homeReached)
                            ?: if (homeReached) "已进入系统，点击“完成”即可同步" else "登录教务系统"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 顶栏快捷跳转：有专属流程的学校由它给出「去哪」与「叫什么」
                    // （例如门户型学校没有固定的 SSO 入口，一键“去登录”只会帮倒忙，
                    //  改为按当前进度跳「回入口页 / 去课表页」）；其余学校沿用通用「去登录」。
                    TextButton(onClick = {
                        val jump = loginFlow?.jumpButton(currentlyLoggedIn())
                        if (jump != null) {
                            AppLog.i("WebLogin", "手动切换页面（${jump.label}）: ${jump.target.take(90)}")
                            webView?.loadUrl(jump.target)
                        } else {
                            AppLog.i("WebLogin", "手动切换到统一身份认证（带 service）")
                            webView?.loadUrl(WebScheduleEngine.ssoUrlWithService())
                        }
                    }) {
                        Text(loginFlow?.jumpButton(currentlyLoggedIn())?.label ?: "去登录")
                    }
                    TextButton(onClick = { requestExit { onDone(currentlyLoggedIn()) } }) { Text("完成") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 阶段提示（醒目）：登录 WebVPN → 登录教务系统 → 点「完成」，随流程推进
            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            // 探针原始输出（调试用，小字灰显）
            if (probeText.isNotBlank()) {
                Text(
                    text = probeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        // ---------- 滑块兼容相关 ----------
                        // 允许手势放大（部分拖动库依赖 touchmove 的 scale 信息），
                        // 但禁用长按选中/震动反馈，避免拖动被“文字选择”抢占。
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        isHapticFeedbackEnabled = false
                        isLongClickable = false
                        // 关闭 overscroll 效果：上滑超过边界会触发“回弹”，
                        // 视觉上正是“滑块弹回”的观感来源之一。
                        overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        // 页面脚本会探测桥的存在，这里给一个空实现即可
                        addJavascriptInterface(KebiaoBridge { _, _ -> }, "KebiaoBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                AppLog.d("WebLogin", "加载完成: ${url?.take(90)}")
                                view.evaluateJavascript(JsScripts.HOOKS, null)
                                // 滑块触摸修复：每个页面都注入一次（登录页/认证页都可能出现滑块）。
                                // 幂等，重复加载不会叠加副作用。返回值记入日志，便于排查是否生效。
                                view.evaluateJavascript(SLIDER_TOUCH_FIX_JS) { r ->
                                    AppLog.i("WebLogin", "滑块触摸补丁注入: $r")
                                }
                                // 北航专用兜底：统一认证外壳页的 iframe 由 init.js 的 onload 设置 src，
                                // 若因任何原因未设置，整页会停留在空白——延迟补上。
                                // 其它学校（如江苏大学 WebVPN）没有这个外壳结构，跳过。
                                val isBuaaSsoShell = url?.contains("sso.buaa.edu.cn") == true &&
                                    url.contains("/login")
                                if (isBuaaSsoShell) {
                                    view.postDelayed({
                                        runCatching { view.evaluateJavascript(SSO_FRAME_FIX, null) }
                                    }, 1_200L)
                                }
                                // 部分拖动库把 handler 绑在 iframe 内部的 document 上，
                                // 外层注入不到；这里延迟补一次，覆盖渲染较慢的验证码容器。
                                view.postDelayed({
                                    runCatching { view.evaluateJavascript(SLIDER_TOUCH_FIX_JS, null) }
                                }, 1_500L)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val u = request.url?.toString().orEmpty()
                                // 外部 CDN（jsdelivr）在国内网络常被墙/挂起；
                                // <link> 样式表会阻塞整页渲染（白屏），而页面自带本地 vant.css，
                                // 这里直接返回空 CSS 跳过它
                                if (u.contains("cdn.jsdelivr.net")) {
                                    AppLog.d("WebLogin", "已跳过外部CDN资源（避免阻塞渲染）: ${u.take(90)}")
                                    return WebResourceResponse(
                                        "text/css",
                                        "utf-8",
                                        java.io.ByteArrayInputStream(ByteArray(0)),
                                    )
                                }
                                return null
                            }
                        }
                        // 首屏地址：有专属流程的学校由流程给出（例如门户型学校打开
                        // **入口页**而不是课表深链 —— 实测直接开深链时门户不给登录入口，
                        // 页面空白，用户无处可登录）；其余学校沿用“会话检测接口”，
                        // 已有会话时直接返回数据、未登录时 302 到 SSO。
                        val entry = loginFlow?.jumpButton(false)?.target ?: WebScheduleEngine.SERVICE_API
                        AppLog.i("WebLogin", "打开首屏: ${entry.take(120)}")
                        loadUrl(entry)
                        webView = this
                    }.also { wv ->
                        // ---------- 滑块验证码兼容补丁 ----------
                        // 现象：滑块“能按但立刻弹回”，即 touchstart 收到、touchmove 中途被截断。
                        // 成因是 WebView 默认会对纵向拖动做“手势归属判定”，把它判给滚动容器，
                        // 从而给 JS 的 touchmove 发 cancel。这里用原始触摸事件转发器绕开该判定。
                        installSliderTouchFix(wv)
                    }
                },
            )
        }
    }

    // 页面销毁/重组时销毁 WebView：释放渲染资源，并避免 WebView 持有 Activity 上下文造成泄漏
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView?.let { wv ->
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.removeJavascriptInterface("KebiaoBridge")
                    wv.destroy()
                }
            }
            webView = null
        }
    }

    // 未登录就点“返回/完成”时的确认提示
    pendingExit?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingExit = null },
            title = { Text("尚未登录成功") },
            text = {
                Text(
                    "还没有检测到成功进入教务系统的课表页，现在返回的话课表无法同步。\n" +
                        "建议先在网页中完成登录（看到课表页面）后再点“完成”。"
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingExit = null }) { Text("继续登录") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExit = null; action() }) { Text("仍要返回") }
            },
        )
    }
}

internal suspend fun probe(view: WebView, school: School?): LoginProbe? =
    withTimeoutOrNull(5_000L) {
        suspendCancellableCoroutine { cont ->
            try {
                // 探针脚本按学校数据选择（学校可用 probeJs 覆盖，适配表格型课表等）；
                // 未提供时用通用 DETECT。这里**不看学校 id**，新增学校无需改本函数。
                val script = school?.probeJs ?: JsScripts.DETECT
                view.evaluateJavascript(script) { value ->
                    if (cont.isActive) cont.resume(parseProbe(value))
                }
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

internal fun parseProbe(value: String?): LoginProbe? {
    if (value == null || value == "null") return null
    return try {
        val json = JSONObject(JSONTokener(value).nextValue() as String)
        LoginProbe(
            url = json.optString("url"),
            login = json.optBoolean("login", false),
            captcha = json.optBoolean("captcha", false),
            grid = json.optBoolean("grid", false),
            app = json.optBoolean("app", false),
            ready = json.optBoolean("ready", false),
            proxied = json.optBoolean("proxied", false),
            notFound = json.optBoolean("notFound", false),
            netError = json.optBoolean("netError", false),
            ssoError = json.optBoolean("ssoError", false),
            error = json.optString("error"),
        )
    } catch (_: Throwable) {
        null
    }
}

/** 解析 URL 主机名（不能用子串判断站点：SSO 的 service 参数里也会出现教务系统的域名/路径） */
internal fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")

/**
 * 滑块验证码触摸兼容补丁。
 *
 * 【要解决的问题】WebVPN / 正方登录页的滑块在 WebView 里「能按下去，但一拖就弹回」。
 *
 * 【成因】WebView 默认会对触摸序列做「手势归属判定」：当手指在拖动过程中带上了纵向分量，
 * WebView 会认为这是「滚动」，于是把手势判给父级滚动容器，并向页面 JS 发送
 * `touchcancel` —— 页面的拖动 handler 因此中断，滑块回弹到起点。
 *
 * 【解法】分两层：
 *  1. Android 层（本函数）：用 setOnTouchListener 接住原始事件并转发给 WebView 的
 *     内置处理逻辑，同时对「单指按压」期间关闭父级滚动拦截，让 touchmove 能完整送达 JS。
 *  2. JS 层（[JsScripts.SLIDER_TOUCH_FIX]，在每个页面加载完成后注入）：
 *     在捕获阶段重绑 touch 事件，屏蔽页面或框架误加的 `touchcancel`，
 *     并把拖动期间的 touchmove 强制 `preventDefault`，阻止浏览器把手势升级成滚动。
 */
private fun installSliderTouchFix(wv: WebView) {
    wv.setOnTouchListener { view, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // 单指按住时禁止父容器抢走手势（关键：这一步阻止纵向分量触发的滚动接管）
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL,
            -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        // 返回 false：不消费事件，交给 WebView 自己的处理管线
        // （返回 true 会让页面完全收不到事件，滑块会彻底失效）
        false
    }
}

/** 页面加载完成后注入的触摸修复脚本（与 [installSliderTouchFix] 配合使用） */
private val SLIDER_TOUCH_FIX_JS: String = JsScripts.SLIDER_TOUCH_FIX
