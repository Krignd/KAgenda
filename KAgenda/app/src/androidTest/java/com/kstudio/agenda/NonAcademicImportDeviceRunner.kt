package com.kstudio.agenda

import android.app.Instrumentation
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only, synthetic inputs. Never reads credentials, invokes cloud AI or changes user schedules. */
class NonAcademicImportDeviceRunner : Instrumentation() {
    private var testAi = false
    private var baseOnly = false
    private var downloadFixture = false
    private var installEngine: com.kstudio.agenda.data.engines.EngineId? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); testAi = arguments?.getString("ai") == "true"; baseOnly = arguments?.getString("optional") == "empty"; downloadFixture = arguments?.getString("download") == "true"; installEngine = com.kstudio.agenda.data.engines.EngineId.find(arguments?.getString("install")); start() }
    override fun onStart() {
        val result = Bundle()
        val root = File(targetContext.cacheDir, "nonacademic-import-test-${System.nanoTime()}")
        try {
            check(android.os.Build.MODEL.contains("sdk") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator only" }
            installEngine?.let {
                runOnMainSync { com.kstudio.agenda.ui.engines.EngineDownloadActivity.open(targetContext, it) }
                com.kstudio.agenda.data.engines.EngineInstaller.install(targetContext, it)
                result.putString("stream", "Requested OS-confirmed install for ${it.key}; UI/system result must be verified separately")
                finish(-1, result); return
            }
            if (baseOnly) {
                Class.forName("com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver", false, targetContext.classLoader)
                Class.forName("com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService", false, targetContext.classLoader)
                for (engine in com.kstudio.agenda.data.engines.EngineId.entries)
                    check(!com.kstudio.agenda.data.engines.EngineFeatures.installed(targetContext, engine))
                check(DocumentTextExtractor.extractBytes("合成纯文本".toByteArray(), "txt").getOrThrow() == "合成纯文本")
                check(DocumentTextExtractor.extractBytes("%PDF".toByteArray(), "pdf", context = targetContext).exceptionOrNull()
                    is com.kstudio.agenda.data.engines.EngineMissingException)
                val ocrMissing = runCatching { runBlocking { ScreenshotTextExtractor.extract(targetContext, Uri.EMPTY) } }.exceptionOrNull()
                check(ocrMissing is com.kstudio.agenda.data.engines.EngineMissingException)
                check(runCatching { com.kstudio.agenda.data.engines.EngineFeatures.requireInstalled(targetContext,
                    com.kstudio.agenda.data.engines.EngineId.AI) }.exceptionOrNull() is com.kstudio.agenda.data.engines.EngineMissingException)
                result.putString("stream", "PASS: base-only app, plain-text import independent; OCR/PDF/AI absent, missing components fail explicitly without partial imports")
                if (downloadFixture) result.putString("downloader", EngineDownloadFixture.run(this))
                finish(-1, result); return
            }
            root.mkdirs()
            for (engine in com.kstudio.agenda.data.engines.EngineId.entries)
                check(com.kstudio.agenda.data.engines.EngineFeatures.installed(targetContext, engine))
            Class.forName("com.kstudio.agenda.data.LocalAiNative", true, targetContext.classLoader)
            result.putString("runtime", "PASS: optional JNI runtime loads without downloading model weights")
            val bitmap = Bitmap.createBitmap(1400, 850, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f }
            canvas.drawText("周一", 400f, 100f, paint)
            canvas.drawText("周二", 950f, 100f, paint)
            canvas.drawText("1-2", 20f, 260f, paint)
            canvas.drawText("高等数学", 400f, 260f, paint)
            canvas.drawText("大学英语", 950f, 260f, paint)
            canvas.drawText("周次：1至16周", 400f, 350f, paint)
            canvas.drawText("周次：2至12周", 950f, 350f, paint)
            canvas.drawText("教室 A101", 400f, 440f, paint)
            val image = File(root, "timetable.png")
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val ocr = runBlocking { ScreenshotTextExtractor.extract(targetContext, Uri.fromFile(image)) }
            check(ocr.contains("高等数学") && ocr.contains("大学英语") && ocr.contains("OCR positions")) { "OCR: $ocr" }
            result.putString("ocr", "PASS: bundled Chinese OCR, weekdays, titles, weeks and positions")

            PDFBoxResourceLoader.init(object : ContextWrapper(context) {
                override fun getApplicationContext(): android.content.Context = this
            })
            val pdfBytes = PDDocument().use { pdf ->
                val page = PDPage(); pdf.addPage(page)
                PDPageContentStream(pdf, page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 14f); stream.newLineAtOffset(40f,700f)
                    stream.showText("Math Monday 08:00-09:35"); stream.endText()
                }
                ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
            }
            check(DocumentTextExtractor.extractBytes(pdfBytes, "pdf", context = targetContext).getOrThrow().contains("Math Monday"))
            check(DocumentTextExtractor.extractBytes(cidPdf(), "pdf", context = targetContext).getOrThrow().contains("高等数学"))
            result.putString("pdf", "PASS: real PDF text and CID hex text with ToUnicode")

            val loaded = CountDownLatch(1)
            lateinit var web: WebView
            runOnMainSync {
                web = WebView(targetContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { loaded.countDown() }
                    }
                    loadDataWithBaseURL("https://timetable.example/", """<html><body>
                        <input value="PRIVATE_PASSWORD"><textarea>PRIVATE_TOKEN</textarea>
                        <table><tr><th>课程名</th><th>星期</th><th>节次</th></tr>
                        <tr><td rowspan="2">高等数学</td><td>周一</td><td>1-2</td></tr></table>
                        </body></html>""", "text/html", "UTF-8", null)
                }
            }
            check(loaded.await(15, TimeUnit.SECONDS)) { "WebView load timeout" }
            val captured = CountDownLatch(1)
            var capture = ""
            runOnMainSync { web.evaluateJavascript(SchedulePageCapture.script) { value ->
                capture = JSONObject(JSONTokener(value).nextValue() as String).getString("text"); captured.countDown()
            } }
            check(captured.await(10, TimeUnit.SECONDS))
            runOnMainSync { web.destroy() }
            check(capture.contains("高等数学") && capture.contains("\"rows\":2") && !capture.contains("PRIVATE_"))
            result.putString("web", "PASS: production DOM capture preserves spans and excludes form values")

            val context = object : ContextWrapper(targetContext) { override fun getFilesDir(): File = File(root,"store").apply { mkdirs() } }
            ExtraCoursesStore.invalidateMemory()
            val course = LocalSmartParser.parseCourses("高等数学 周一第1-2节 1-4周 A101").single()
            val monday = LocalDate.of(2026,9,7)
            check(ExtraCoursesStore.addAll(context, listOf(course), monday) == 1)
            check(ExtraCoursesStore.addAll(context, listOf(course.copy(weeksRaw="8-12")), monday) == 0)
            ExtraCoursesStore.invalidateMemory(); ExtraCoursesStore.ensureLoaded(context)
            check(ExtraCoursesStore.courses.value.single().weeksSet() == setOf(1,2,3,4,8,9,10,11,12))
            check(ExtraCoursesStore.anchorEpochDay.value == monday.toEpochDay())
            check(runCatching { ExtraCoursesStore.addAll(context, listOf(course.copy(teacher="different")), monday) }.isFailure)
            result.putString("storage", "PASS: isolated persistence, reload, union of weeks, duplicate/conflict protection")
            if (testAi) {
                val source = "课程名\t星期\t节次\t周次\t教室\t教师\n高等数学\t周一\t第1-2节\t1-4周\tA101\t张三\n大学英语\t周三\t第3-4节\t2,4,6周\tB202\t李四"
                check(runBlocking { SettingsStore.read(targetContext) }.aiProvider == "local") { "Local AI test requires local mode" }
                val reply = runBlocking { CourseAiParser.convert(targetContext, source) }
                val ai = CourseImportCodec.decode(reply)
                check(ai.issues.isEmpty() && ai.courses.size == 2) { "AI incomplete: $reply" }
                check(ai.courses.first { it.title == "高等数学" }.weeksSet() == setOf(1,2,3,4)) { "AI wrong weeks: $reply" }
                check(ai.courses.first { it.title == "大学英语" }.let { it.dayOfWeek == 3 && it.startPeriod == 3 && it.endPeriod == 4 && it.weeksSet() == setOf(2,4,6) }) { "AI wrong slot: $reply" }
                result.putString("ai", "PASS: actual local Qwen conversion preserves both slots and disjoint weeks")
                val imageReply = runBlocking { CourseAiParser.convert(targetContext, ocr) }
                val fromImage = CourseImportCodec.decode(imageReply)
                check(fromImage.issues.isEmpty() && fromImage.courses.size == 2) { "Screenshot AI incomplete: $imageReply; OCR=$ocr" }
                check(fromImage.courses.first { it.title == "高等数学" }.let { it.dayOfWeek == 1 && it.startPeriod == 1 && it.endPeriod == 2 && it.weeksSet() == (1..16).toSet() }) { "Screenshot math wrong: $imageReply; OCR=$ocr" }
                check(fromImage.courses.first { it.title == "大学英语" }.let { it.dayOfWeek == 2 && it.startPeriod == 1 && it.endPeriod == 2 && it.weeksSet() == (2..12).toSet() }) { "Screenshot English wrong: $imageReply; OCR=$ocr" }
            }
            result.putString("stream", "OK: OCR, PDF, DOM capture and persistence passed\n")
            finish(0, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n"); finish(1,result)
        } finally {
            ExtraCoursesStore.invalidateMemory()
            root.deleteRecursively()
        }
    }

    private fun cidPdf(): ByteArray {
        val cmap = "/CIDInit /ProcSet findresource begin 12 dict begin begincmap /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def /CMapName /Test def /CMapType 2 def 1 begincodespacerange <0000> <FFFF> endcodespacerange 4 beginbfchar <0001> <9AD8> <0002> <7B49> <0003> <6570> <0004> <5B66> endbfchar endcmap CMapName currentdict /CMap defineresource pop end end"
        val content = "BT /F1 14 Tf 40 700 Td <0001000200030004> Tj ET"
        val objects = listOf("<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 4 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /Identity-H /DescendantFonts [5 0 R] /ToUnicode 7 0 R >>",
            "<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 0 >> >>",
            "<< /Length ${content.length} >>\nstream\n$content\nendstream", "<< /Length ${cmap.length} >>\nstream\n$cmap\nendstream")
        val text = StringBuilder("%PDF-1.4\n"); val offsets = mutableListOf<Int>()
        objects.forEachIndexed { i, obj -> offsets += text.length; text.append("${i+1} 0 obj\n$obj\nendobj\n") }
        val xref = text.length
        text.append("xref\n0 8\n0000000000 65535 f \n")
        offsets.forEach { text.append("%010d 00000 n \n".format(it)) }
        text.append("trailer\n<< /Size 8 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
        return text.toString().toByteArray(Charsets.US_ASCII)
    }
}
