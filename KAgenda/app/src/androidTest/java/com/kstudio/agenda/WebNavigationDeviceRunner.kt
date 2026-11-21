package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.WebDocumentGate
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.model.School
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

/** 独立模拟器、内存 HTTPS 页面；直接调用生产认证流程，不读取账号、不访问学校服务器。 */
class WebNavigationDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.MODEL.contains("sdk"))
            runBlocking(Dispatchers.Main) { verify() }
            result.putString("stream", "PASS: production SSO rejects stale error DOM during delayed navigation; completed service return; same-URL reload; in-place login error; synthetic HTTPS only")
            finish(-1, result)
        } catch (t: Throwable) {
            result.putString("stream", android.util.Log.getStackTraceString(t)); finish(1, result)
        }
    }

    private suspend fun js(view: WebView, script: String): String = suspendCancellableCoroutine { c ->
        view.evaluateJavascript(script) { raw ->
            if (c.isActive) c.resume(JSONTokener(raw).nextValue().toString())
        }
    }

    private suspend fun until(condition: suspend () -> Boolean) {
        repeat(150) { if (condition()) return; delay(80) }; error("Fixture timeout")
    }

    private suspend fun invoke(engine: WebScheduleEngine, name: String, vararg args: Any): Any? =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = engine.javaClass.declaredMethods.single { it.name == name }
            method.isAccessible = true
            method.invoke(engine, *args, continuation)
        }

    private fun field(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).run {
        isAccessible = true; get(value)
    }

    private suspend fun verify() {
        val view = WebView(targetContext)
        try {
            val engine = WebScheduleEngine(targetContext); engine.attach(view)
            check(js(view, WebDocumentGate.markScript("initial-blank")) == "true")
            val homeRequests = AtomicInteger(); val authRequests = AtomicInteger()
            var mode = "login"
            val school = School("navigation-fixture", "Fixture", "https://auth.navigation.invalid/login",
                "https://portal.navigation.invalid/home", "https://portal.navigation.invalid/api", true,
                probeJs = """(function(){var k=document.body.dataset.page;return JSON.stringify({url:location.href,login:k==='login',app:k==='home',netError:k==='error',error:document.body.dataset.error||''});})();""")
            engine.javaClass.getDeclaredField("school").apply { isAccessible = true }.set(engine, school)
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse {
                    check(r.url.host.orEmpty().endsWith("navigation.invalid"))
                    if (!r.isForMainFrame) return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                    val body = when {
                        r.url.host == "auth.navigation.invalid" -> {
                            authRequests.incrementAndGet(); Thread.sleep(1400)
                            if (mode == "login") "<body data-page='login'><input type='password'></body>"
                            else "<script>location.replace('https://portal.navigation.invalid/api')</script>"
                        }
                        r.url.path == "/api" -> "<body>service accepted</body>"
                        else -> {
                            val count = homeRequests.incrementAndGet(); Thread.sleep(800)
                            "<body data-page='${if (count == 1) "error" else "home"}'>fixture</body>"
                        }
                    }
                    return WebResourceResponse("text/html", "UTF-8", body.byteInputStream())
                }
            }
            view.loadUrl(school.homeUrl)
            until { js(view, "JSON.stringify(document.body&&document.body.dataset.page==='error')") == "true" }

            // 旧错误页仍在教务主机上；旧实现会立即误判CAS已回跳并打断登录导航。
            val login = requireNotNull(invoke(engine, "openSsoAndWait", view))
            check(field(login, "hasLoginForm") == true)
            check(authRequests.get() == 1 && homeRequests.get() == 1) { "Premature home navigation" }

            // 登录失败可由原文档原地更新；文档屏障不能吞掉明确的口令拒绝。
            val marker = "in-place-error"
            js(view, WebDocumentGate.markScript(marker))
            js(view, "document.body.dataset.error='密码错误';JSON.stringify(true)")
            val predicate: (Any) -> Boolean = { field(it, "hasLoginForm") == true && field(it, "error") == "密码错误" }
            val rejected = requireNotNull(invoke(engine, "waitFor", view, 3000L, marker, true, predicate))
            check(field(rejected, "error") == "密码错误")

            // 现有认证会话真实回到service接口后，生产流程才能继续导航首页。
            mode = "existing-session"
            val ready = requireNotNull(invoke(engine, "openSsoAndWait", view))
            check(field(ready, "hasApp") == true && homeRequests.get() == 2)

            js(view, WebDocumentGate.markScript("same-url"))
            check(js(view, WebDocumentGate.readyScript("same-url")) == "false")
            view.reload(); delay(100)
            check(js(view, WebDocumentGate.readyScript("same-url")) == "false")
            until { js(view, WebDocumentGate.readyScript("same-url")) == "true" }
            check(homeRequests.get() == 3)
        } finally { view.destroy() }
    }
}
