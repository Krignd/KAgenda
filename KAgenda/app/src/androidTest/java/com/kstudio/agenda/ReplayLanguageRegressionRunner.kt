package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.JsScripts
import com.kstudio.agenda.data.ScheduleParser
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue

/** Real WebView runtime, synthetic data only. All network requests are blocked. */
class ReplayLanguageRegressionRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var resultCode = android.app.Activity.RESULT_OK
        var view: WebView? = null
        val requestLanguages = ConcurrentLinkedQueue<String>()
        try {
            check(android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            val loaded = CountDownLatch(1)
            runOnMainSync {
                view = WebView(targetContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse {
                            // Intercept the real Chromium fetch before any network I/O; no fetch mock.
                            val data = if (r.url.path.orEmpty().contains("getMyScheduleDetail")) {
                                val language = r.requestHeaders.entries.firstOrNull { it.key.equals("Accept-Language", true) }?.value.orEmpty()
                                requestLanguages.add(language)
                                val name = if (language == "zh-CN") "合成课程" else "SYNTHETIC COURSE"
                                """{"datas":{"arrangedList":[{"courseName":${JSONObject.quote(name)},"dayOfWeek":2,"beginSection":1,"endSection":2,"cellDetail":[]}]}}"""
                            } else "{}"
                            return WebResourceResponse("application/json", "UTF-8", java.io.ByteArrayInputStream(data.toByteArray(Charsets.UTF_8)))
                        }
                        override fun onPageFinished(v: WebView, url: String?) { loaded.countDown() }
                    }
                    loadDataWithBaseURL("https://synthetic.invalid/", "<html lang='en'><body>Synthetic</body></html>", "text/html", "UTF-8", null)
                }
            }
            check(loaded.await(10, TimeUnit.SECONDS))
            fun eval(script: String): String {
                var value = "null"
                val latch = CountDownLatch(1)
                runOnMainSync { view!!.evaluateJavascript(script) { value = it; latch.countDown() } }
                check(latch.await(5, TimeUnit.SECONDS))
                return value
            }
            for (locale in listOf("", "zh-CN", "en-US")) {
                requestLanguages.clear()
                val selected = if (locale.isEmpty()) "zh-CN" else locale
                eval("""
                    localStorage.removeItem('umi_locale');
                    ${if (locale.isEmpty()) "" else "localStorage.setItem('umi_locale', ${JSONObject.quote(locale)});"}
                """.trimIndent())
                val probe = JSONObject(JSONTokener(eval(JsScripts.LANGUAGE_PROBE)).nextValue() as String)
                check(probe.getString("pageLang") == "en") // HTML language is not the request locale.
                check(probe.getString("portalLocale") == locale)
                eval(JsScripts.fetchWeeksScript("2026-2027-1", "", 1))
                val deadline = System.currentTimeMillis() + 5000
                while (eval("!!window.__kbWeeksBusy") == "true" && System.currentTimeMillis() < deadline) Thread.sleep(25)
                check(eval("!!window.__kbWeeksBusy") == "false")
                val raw = JSONTokener(eval("window.__kbWeeks")).nextValue() as String
                val json = JSONObject(raw)
                check(json.getJSONObject("debug").getString("requestLanguage") == selected)
                check(requestLanguages.toList() == listOf(selected)) { "Chromium request language differs" }
                val expected = if (selected == "zh-CN") "合成课程" else "SYNTHETIC COURSE"
                check(ScheduleParser.parseSemesterFetchResult(raw).getValue(1).single().title == expected)
                check(eval("localStorage.getItem('umi_locale')") == if (locale.isEmpty()) "null" else JSONObject.quote(locale))
            }
            result.putString("stream", "PASS: 3 real WebView synthetic replay scenarios with Chromium request-header interception; default Chinese, explicit Chinese, explicit English; no network or school accounts; original parser.")
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}")
            resultCode = android.app.Activity.RESULT_CANCELED
        } finally { runOnMainSync { view?.destroy() } }
        finish(resultCode, result)
    }
}
