package com.kstudio.agenda.data

import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.util.AppLog
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** Read-only, allowlisted diagnostics; never logs URLs, headers other than language, or page content. */
object WebViewLanguageDiagnostics {
    private fun language(value: String): String = value.takeIf {
        it.length <= 80 && it.matches(Regex("[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*"))
    } ?: "未提供"

    fun environment(view: WebView, source: String) {
        runCatching {
            val engine = WebView.getCurrentWebViewPackage()?.versionName.orEmpty()
                .replace(Regex("[^0-9.]"), "").take(40)
            val base = "app=${AppText.state.value}, system=${language(Locale.getDefault().toLanguageTag())}, " +
                "resource=${language(view.resources.configuration.locales[0].toLanguageTag())}, WebView=$engine"
            view.evaluateJavascript(JsScripts.LANGUAGE_PROBE) { raw ->
                val page = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull()
                AppLog.i(source, "课名诊断[环境]: $base, page=${language(page?.optString("pageLang").orEmpty())}, " +
                    "navigator=${language(page?.optString("browserLang").orEmpty())}, " +
                    "portalLocale=${language(page?.optString("portalLocale").orEmpty())}")
            }
        }
    }

    fun request(request: WebResourceRequest, source: String) {
        if (!request.url.path.orEmpty().contains("getMyScheduleDetail")) return
        val value = request.requestHeaders.entries.firstOrNull { it.key.equals("Accept-Language", true) }
            ?.value.orEmpty().replace(Regex("[^A-Za-z0-9,;=._* -]"), "").take(80)
        AppLog.i(source, "课名诊断[请求]: Accept-Language可见值=${value.ifBlank { "未提供" }}")
    }

    fun resourceError(request: WebResourceRequest, code: Int, http: Boolean, source: String) {
        val kind = when (request.url.path.orEmpty().substringAfterLast('.').lowercase(Locale.ROOT)) {
            "js" -> "script"; "css" -> "style"; "woff", "woff2", "ttf" -> "font"
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> "image"
            else -> "other"
        }
        val host = request.url.host.orEmpty().take(253).replace(Regex("[^A-Za-z0-9.-]"), "")
        AppLog.w(source, "课名诊断[资源错误]: main=${request.isForMainFrame}, kind=$kind, host=$host, " +
            "${if (http) "HTTP" else "WebView"}=$code")
    }

    fun console(message: ConsoleMessage, source: String) {
        if (message.messageLevel() != ConsoleMessage.MessageLevel.ERROR) return
        val text = message.message().orEmpty()
        val kind = listOf("SyntaxError", "TypeError", "ReferenceError", "SecurityError", "CORS", "Content Security Policy")
            .firstOrNull { text.contains(it, ignoreCase = true) } ?: "other"
        AppLog.w(source, "课名诊断[脚本错误]: kind=$kind, line=${message.lineNumber()}")
    }
}
