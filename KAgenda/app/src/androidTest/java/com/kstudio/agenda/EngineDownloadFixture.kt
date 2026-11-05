package com.kstudio.agenda

import android.app.Instrumentation
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kstudio.agenda.data.engines.*
import com.kstudio.agenda.ui.engines.EngineDownloadActivity
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/** Test APK assets + in-memory HTTPS handler. No real server, personal data or code execution. */
object EngineDownloadFixture {
    @Volatile private var mode = "good"
    private val ranged = AtomicInteger()
    private val apkRequests = AtomicInteger()

    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        val raw = instrumentation.context.assets.open("engine-download-fixtures/manifest.json").bufferedReader().use { it.readText() }
        val payloads = mutableMapOf<String, ByteArray>()
        for (engine in EngineId.entries) {
            val plan = EnginePackPlan.parse(raw, engine, BuildConfig.VERSION_CODE, android.os.Build.SUPPORTED_ABIS.toList())
            for (file in plan.files) {
                payloads[file.name] = instrumentation.context.assets.open("engine-download-fixtures/${file.name}").use { it.readBytes() }
                // Emulator-only fixture cache: source revisions can change these same-version APKs.
                // Only remove the exact test manifest's APK; never touch model or schedule storage.
                val cached = File(EnginePacks.directory(context, engine), file.name)
                if (cached.exists()) {
                    check(cached.delete())
                }
            }
        }
        URL.setURLStreamHandlerFactory { protocol -> if (protocol != "https") null else object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = object : HttpsURLConnection(url) {
                private var content = ByteArray(0)
                private var offset = 0
                private var code = 0
                override fun getResponseCode(): Int {
                    if (code != 0) return code
                    if (url.toString() == EnginePacks.manifestUrl || url.toString() == EnginePacks.fallbackManifestUrl) {
                        if (mode == "primary-missing" && url.toString() == EnginePacks.manifestUrl) { code = 404; return code }
                        val manifest = if (mode == "wrong-base") JSONObject(raw).put("baseApkSha256", JSONObject().put("fixture", "0".repeat(64))).toString() else raw
                        content = manifest.toByteArray(); code = 200
                    } else {
                        val name = url.path.substringAfterLast('/')
                        val bytes = payloads[name] ?: run { code = 404; return code }
                        apkRequests.incrementAndGet(); content = bytes.copyOf()
                        if (mode == "corrupt") content[content.lastIndex] = (content.last().toInt() xor 1).toByte()
                        val requested = getRequestProperty("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        if (requested > 0) ranged.incrementAndGet()
                        offset = if (mode == "ignore-range") 0 else requested
                        code = if (offset > 0) 206 else 200
                    }
                    return code
                }
                override fun getInputStream(): java.io.InputStream { getResponseCode(); return ByteArrayInputStream(content, offset, content.size - offset) }
                override fun getHeaderField(name: String?): String? {
                    getResponseCode(); return if (name.equals("Content-Range", true) && code == 206) "bytes $offset-${content.lastIndex}/${content.size}" else null
                }
                override fun connect() { }
                override fun disconnect() { }
                override fun usingProxy() = false
                override fun getCipherSuite() = "synthetic-local-fixture"
                override fun getLocalCertificates(): Array<Certificate>? = null
                override fun getServerCertificates(): Array<Certificate> = emptyArray()
            }
        } }
        instrumentation.runOnMainSync { EngineDownloadActivity.open(context, EngineId.OCR) }
        fun task(engine: EngineId, expected: WorkInfo.State) {
            val request = OneTimeWorkRequestBuilder<EngineDownloadWorker>().setInputData(workDataOf("engine" to engine.key)).build()
            val manager = WorkManager.getInstance(context); manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
            val deadline = android.os.SystemClock.elapsedRealtime() + 45000
            var state: WorkInfo.State
            do {
                state = requireNotNull(manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)).state
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Worker timeout: $engine $state" }
                if (!state.isFinished) android.os.SystemClock.sleep(40)
            } while (!state.isFinished)
            check(state == expected) { "Worker $engine: $state expected $expected" }
        }
        // Wrong base must reject before any APK fetch, even when versionCode is identical.
        mode = "wrong-base"; task(EngineId.OCR, WorkInfo.State.FAILED); check(apkRequests.get() == 0)
        mode = "corrupt"; task(EngineId.PDF, WorkInfo.State.FAILED)
        val pdf = EnginePackPlan.parse(raw, EngineId.PDF, BuildConfig.VERSION_CODE, android.os.Build.SUPPORTED_ABIS.toList())
        check(!File(EnginePacks.directory(context, EngineId.PDF), pdf.files.single().name + ".part").exists())
        for (engine in EngineId.entries) {
            val plan = EnginePackPlan.parse(raw, engine, BuildConfig.VERSION_CODE, android.os.Build.SUPPORTED_ABIS.toList())
            val file = plan.files.single()
            File(EnginePacks.directory(context, engine), file.name + ".part").writeBytes(payloads.getValue(file.name).copyOfRange(0, 4096))
            mode = if (engine == EngineId.PDF) "ignore-range" else if (engine == EngineId.AI) "primary-missing" else "good"
            task(engine, WorkInfo.State.SUCCEEDED)
            val actual = File(EnginePacks.directory(context, engine), file.name)
            check(actual.length() == file.bytes && EnginePacks.hash(actual) == file.sha256)
            check(!EngineFeatures.installed(context, engine)) // Downloading alone never installs or loads code.
        }
        check(ranged.get() >= 3)
        return "PASS: production WorkManager downloader, wrong-base refusal before APK fetch, corrupt-body rejection/deletion, 206 resume and 200 replacement, all3packs SHA-256/cache receipts; in-memory HTTPS only, no installs"
    }
}
