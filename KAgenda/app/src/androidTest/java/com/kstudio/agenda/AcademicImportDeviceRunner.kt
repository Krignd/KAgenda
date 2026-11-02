package com.kstudio.agenda

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.kstudio.agenda.data.*
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.ui.WarehouseImportActivity
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONTokener

/** Synthetic pages only, on an emulator; exercises production capture, bridge, preview and save. */
class AcademicImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            val ctx = targetContext
            uiAutomation.waitForIdle(100, 5000)
            check(SchoolCatalog.load(ctx, Schools.ALL).size >= 3196)
            Schools.loadCatalog(ctx)
            AppText.state.value = AppLang.ZH
            val original = runBlocking { SettingsStore.read(ctx) }
            val school = Schools.all().first { !it.supported && !it.custom && it.warehouseId.isBlank() && it.homeUrl.isBlank() }
            runBlocking { SettingsStore.setSchool(ctx, school.id) }; Schools.setCurrent(school.id)
            val activity = startActivitySync(Intent(ctx, WarehouseImportActivity::class.java)
                .putExtra("schoolId", school.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WarehouseImportActivity
            try {
                fun <T> onMain(block: () -> T): T { var value: T? = null; runOnMainSync { value = block() }; @Suppress("UNCHECKED_CAST") return value as T }
                fun waitFor(block: () -> Boolean) {
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    while (SystemClock.elapsedRealtime() < deadline) { if (onMain(block)) return; SystemClock.sleep(50) }
                    val state = onMain {
                        val status = activity.javaClass.getDeclaredField("status\$delegate").apply { isAccessible = true }.get(activity) as MutableState<*>
                        "status=${status.value}; url=${findWebForReport(activity.window.decorView)?.url}"
                    }
                    error("Timed out waiting for academic UI: $state")
                }
                fun findWeb(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
                    (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) } else null
                waitFor { findWeb(activity.window.decorView) != null }
                val web = onMain { findWeb(activity.window.decorView)!! }
                val index = SchoolCatalog.adapters(school).indexOfFirst { it.parser == "qz" }; check(index >= 0)
                @Suppress("UNCHECKED_CAST")
                val batch = activity.javaClass.getDeclaredField("batch\$delegate").apply { isAccessible = true }.get(activity) as MutableState<WarehousePayload.Batch?>
                val busy = activity.javaClass.getDeclaredField("busy\$delegate").apply { isAccessible = true }.get(activity) as MutableState<*>
                val adapter = activity.javaClass.getDeclaredField("adapterIndex\$delegate").apply { isAccessible = true }.get(activity) as MutableIntState
                onMain { adapter.intValue = index }
                val html = """<html><body><table id="kbtable"><tr><th>星期</th></tr><tr><td><div class="kbcontent">模拟教务课程<br><font title="老师">模拟教师</font><br><font title="周次(节次)">1-5(单)(周)</font><br><font title="教室">A201</font></div></td></tr></table></body></html>"""
                check(AcademicPortalParser.parse("qz", listOf(html)).courses.single().title == "模拟教务课程")
                val responseBody = AtomicReference(html)
                val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
                val thread = Thread {
                    while (!server.isClosed) runCatching {
                        server.accept().use { socket ->
                            val input = socket.getInputStream().bufferedReader()
                            while (!input.readLine().isNullOrEmpty()) { /* consume request headers */ }
                            val bytes = responseBody.get().toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(bytes); flush()
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
                var pageNumber = 0
                fun evaluate(js: String): String {
                    val latch = CountDownLatch(1)
                    var value = "null"
                    onMain { web.evaluateJavascript(js) { value = it; latch.countDown() } }
                    check(latch.await(5, TimeUnit.SECONDS))
                    return value
                }
                fun load(source: String) {
                    responseBody.set(source)
                    val url = "http://127.0.0.1:${server.localPort}/${pageNumber++}"
                    onMain { web.loadUrl(url) }
                    waitFor { web.url == url && web.progress == 100 }; waitForIdleSync()
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    val marker = if (source.contains("kbtable")) "kbtable" else "尚未登录"
                    while (true) {
                        val current = JSONTokener(evaluate("document.documentElement.outerHTML")).nextValue().toString()
                        if (current.contains(marker)) {
                            if (marker == "kbtable") check(AcademicPortalParser.parse("qz", listOf(current)).courses.isNotEmpty())
                            break
                        }
                        check(SystemClock.elapsedRealtime() < deadline) { "Fixture page did not finish loading" }
                        SystemClock.sleep(50)
                    }
                }
                fun extract() { onMain { activity.javaClass.getDeclaredMethod("runScript").apply { isAccessible = true }.invoke(activity) } }
                val cache = File(ctx.filesDir, "schedule_cache.json")
                val before = if (cache.exists()) cache.readBytes().toList() else null
                load(html); extract(); waitFor { batch.value != null }
                check(onMain { batch.value!!.courses.single().weeksSet() } == setOf(1,3,5))
                waitForIdleSync()
                val uiDeadline = SystemClock.elapsedRealtime() + 5000
                while (uiAutomation.rootInActiveWindow == null && SystemClock.elapsedRealtime() < uiDeadline) SystemClock.sleep(50)
                SystemClock.sleep(500)
                File(ctx.getExternalFilesDir(null), "academic-preview.png").outputStream().use {
                    check(uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                check(uiAutomation.rootInActiveWindow?.isVisibleToUser == true) { "Preview window is not visible" }
                check((if (cache.exists()) cache.readBytes().toList() else null) == before) { "Preview wrote the cache" }
                onMain { activity.javaClass.getDeclaredMethod("fail", String::class.java).apply { isAccessible = true }.invoke(activity, "") }
                load("<html><body>尚未登录，无课程</body></html>"); extract(); waitFor { busy.value == false }
                check(onMain { batch.value == null })
                check((if (cache.exists()) cache.readBytes().toList() else null) == before) { "Failure overwrote the cache" }
                load(html); extract(); waitFor { batch.value != null }
                val ready = onMain { batch.value!! }
                onMain { activity.javaClass.getDeclaredMethod("save", WarehousePayload.Batch::class.java, LocalDate::class.java)
                    .apply { isAccessible = true }.invoke(activity, ready, LocalDate.of(2026, 9, 7)) }
                waitFor { activity.isFinishing }
                val saved = ScheduleCache.load(ctx)!!
                check(saved.weeks[1]?.single()?.title == "模拟教务课程")
                check(saved.weeks[2].orEmpty().isEmpty())
                check(saved.weeks[5]?.single()?.room == "A201")
                server.close(); thread.join(1000)
                result.putString("stream", "PASS: production WebView capture -> bridge -> Kotlin parser -> preview -> explicit save; failure and preview kept cache unchanged; odd weeks retained.")
            } finally {
                runOnMainSync { if (!activity.isFinishing) activity.finish() }
                runBlocking { SettingsStore.setSchool(ctx, original.schoolId) }; Schools.setCurrent(original.schoolId)
            }
            finish(-1, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString().take(4000)}")
            finish(0, result)
        }
    }
    private fun findWebForReport(view: View): WebView? = if (view is WebView) view else if (view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull { findWebForReport(view.getChildAt(it)) } else null
}
