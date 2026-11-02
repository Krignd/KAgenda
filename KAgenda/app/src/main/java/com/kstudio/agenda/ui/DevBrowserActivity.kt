@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import com.kstudio.agenda.data.SchedulePageCapture
import com.kstudio.agenda.i18n.ImportText
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONTokener
import org.json.JSONObject
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import com.kstudio.agenda.data.WebViewLanguageDiagnostics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import com.kstudio.agenda.ui.theme.FrostedAlertDialog as AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kstudio.agenda.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.kstudio.agenda.ui.components.AppTextButton as TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kstudio.agenda.data.BrowserHistoryEntry
import com.kstudio.agenda.data.BrowserHistoryStore
import com.kstudio.agenda.data.SchoolFlows
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.ui.theme.KAgendaTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 开发者工具里的「内置浏览器」。
 *
 * 刻意做成**普通浏览器**：不注入任何脚本、不加 JS 桥、不自动跳转、不改 UA，
 * 只提供后退 / 前进 / 刷新 / 地址栏 / 访问记录。用途是让开发者手动浏览门户与
 * 教务系统，观察真实页面流程（登录流程窗口 WebLoginActivity 是另一套，带自动
 * 跳转与探针，两者互不影响；Cookie 由全进程共享的 CookieManager 自然共享）。
 *
 * 与 WebLoginActivity 的区别：
 * - 这里**不**注入 HOOKS / UJS_DETECT / 滑块触摸补丁，页面行为与系统浏览器一致；
 * - 这里**不**做任何自动导航，用户点哪里就去哪里；
 * - 这里会记录访问历史（可回看、可点回、可清空），登录窗口不记录。
 */
class DevBrowserActivity : ComponentActivity() {
    companion object {
        const val EXTRA_IMPORT = "schedule_import_mode"
        const val EXTRA_PAGE = "schedule_page_text"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KAgendaTheme {
                DevBrowserScreen(onClose = { finish() }, importMode = intent.getBooleanExtra(EXTRA_IMPORT, false),
                    onCaptured = { page -> setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PAGE, page)); finish() })
            }
        }
    }
}

@Composable
private fun DevBrowserScreen(onClose: () -> Unit, importMode: Boolean = false, onCaptured: (String) -> Unit = {}) {
    val t = LocalStrings.current
    val context = LocalContext.current
    var captureError by remember { mutableStateOf("") }
    var capturing by remember { mutableStateOf(false) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    /** 起始地址：江苏大学用 WebVPN 门户，其它学校用其教务首页；读完设置后再生效 */
    var startUrl by remember { mutableStateOf<String?>(null) }
    var addressInput by remember { mutableStateOf("") }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<BrowserHistoryEntry>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = SettingsStore.read(context)
        val school = Schools.of(settings.schoolId)
        // 起始页由学校插件决定（插件默认给教务首页；门户型学校可改成门户登录页）
        startUrl = SchoolFlows.of(school).devBrowserStartUrl(school)
        history = BrowserHistoryStore.load(context)
    }

    fun go(raw: String) {
        val v = webView ?: return
        val url = normalizeUrl(raw)
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        if (parsed?.scheme !in listOf("https", "http") || parsed?.host.isNullOrBlank() || parsed?.userInfo != null) {
            captureError = ImportText.badAddress
            return
        }
        captureError = ""
        v.loadUrl(url)
    }

    fun refreshNavState(v: WebView) {
        canBack = v.canGoBack()
        canForward = v.canGoForward()
    }

    // 起始地址是异步读到的（LaunchedEffect），而 AndroidView 的 factory 只执行一次 ——
    // 若在 factory 里 loadUrl，读到时 WebView 早已建好，页面会一直空白。
    // 因此放在这里：WebView 与地址都就绪后补一次导航（只在尚未打开任何页面时）。
    LaunchedEffect(startUrl, webView) {
        val url = startUrl ?: return@LaunchedEffect
        val v = webView ?: return@LaunchedEffect
        val current = v.url.orEmpty()
        if (current.isBlank() || current.startsWith("about:")) {
            addressInput = url
            v.loadUrl(url)
        }
    }

    // 系统返回键：能回退就先回退，否则关闭本页
    BackHandler {
        val v = webView
        if (v != null && v.canGoBack()) v.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (importMode) ImportText.browser else t.devBrowserTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = t.devBrowserClose)
                    }
                },
                actions = {
                    IconButton(
                        enabled = canBack,
                        onClick = { webView?.goBack() },
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back) }
                    IconButton(
                        enabled = canForward,
                        onClick = { webView?.goForward() },
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = t.devBrowserForward) }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = t.devBrowserRefresh)
                    }
                    IconButton(onClick = {
                        history = BrowserHistoryStore.load(context)
                        showHistory = true
                    }) { Icon(Icons.Filled.History, contentDescription = t.devBrowserHistory) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 地址栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    singleLine = true,
                    label = { Text(t.devBrowserAddress) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { go(addressInput) }) { Text(t.devBrowserGo) }
            }

            if (importMode) {
                Text(ImportText.browserHint, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !capturing, onClick = {
                    val current = webView ?: return@TextButton
                    capturing = true
                    captureError = ""
                    current.evaluateJavascript(SchedulePageCapture.script) { result ->
                        capturing = false
                        runCatching {
                            val json = JSONObject(JSONTokener(result).nextValue() as String)
                            require(!json.has("error")) { ImportText.tooLong }
                            val page = json.optString("text")
                            require(page.isNotBlank()) { ImportText.noText }
                            onCaptured(page)
                        }.onFailure { captureError = it.message ?: ImportText.failed }
                    }
                }) { Text(if (capturing) ImportText.parsing else ImportText.capture) }
            }
            if (captureError.isNotBlank()) Text(captureError, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)

            // 普通 WebView：开启 JS / 本地存储 / 缩放；只读语言探针不改页面或请求。
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                WebViewLanguageDiagnostics.console(message, "DevBrowser")
                                return super.onConsoleMessage(message)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                WebViewLanguageDiagnostics.request(request, "DevBrowser")
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                WebViewLanguageDiagnostics.resourceError(request, error.errorCode, false, "DevBrowser")
                                super.onReceivedError(view, request, error)
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                WebViewLanguageDiagnostics.resourceError(request, response.statusCode, true, "DevBrowser")
                                super.onReceivedHttpError(view, request, response)
                            }
                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                if (url != null) {
                                    addressInput = url
                                    // 访问记录：页面一开始加载就记（标题稍后回填）
                                    BrowserHistoryStore.record(context, url)
                                }
                                refreshNavState(view)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                WebViewLanguageDiagnostics.environment(view, "DevBrowser")
                                if (url != null) {
                                    addressInput = url
                                    BrowserHistoryStore.updateTitle(context, url, view.title.orEmpty())
                                }
                                refreshNavState(view)
                            }

                            // 站内跳转/重定向也记一笔（onPageStarted 对纯锚点不会触发）
                            override fun doUpdateVisitedHistory(
                                view: WebView,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                if (url != null && !isReload) BrowserHistoryStore.record(context, url)
                                refreshNavState(view)
                            }
                        }
                        webView = this
                    }
                },
            )
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(t.devBrowserHistory) },
            text = {
                if (history.isEmpty()) {
                    Text(t.devBrowserEmpty)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(history) { entry ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHistory = false
                                        addressInput = entry.url
                                        webView?.loadUrl(entry.url)
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = entry.title.ifBlank { entry.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatTime(entry.atMillis) + "  " + entry.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text(t.devBrowserClose) }
            },
            dismissButton = {
                TextButton(onClick = {
                    BrowserHistoryStore.clear(context)
                    history = emptyList()
                }) { Text(t.devBrowserClear) }
            },
        )
    }

    // 页面销毁时释放 WebView，避免持有 Activity 上下文
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView?.let { v ->
                    (v.parent as? android.view.ViewGroup)?.removeView(v)
                    v.destroy()
                }
            }
            webView = null
        }
    }
}

/** 地址栏输入规整：没写协议时按 https 处理（普通浏览器习惯） */
private fun normalizeUrl(raw: String): String {
    val s = raw.trim()
    if (s.isBlank()) return ""
    return if (s.contains("://")) s else "https://$s"
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
