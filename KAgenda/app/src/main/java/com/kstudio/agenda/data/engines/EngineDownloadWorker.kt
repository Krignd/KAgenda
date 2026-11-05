package com.kstudio.agenda.data.engines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.R
import com.kstudio.agenda.data.BoundedInput
import com.kstudio.agenda.data.ModelDownloadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object EnginePacks {
    val supportedAbis: List<String> get() = (if (android.os.Process.is64Bit())
        Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).toList()
    // This versioned endpoint is published together with the corresponding base and signed split APKs.
    val manifestUrl get() = "https://20071009.xyz/KAgenda/engines/${BuildConfig.VERSION_CODE}/manifest.json"
    val fallbackManifestUrl get() = "https://github.com/Krignd/KAgenda/releases/download/" +
        BuildConfig.VERSION_NAME.replace(" ", "-").replace("(", "-").replace(")", "") + "/manifest.json"
    fun directory(context: Context, engine: EngineId) =
        File(context.noBackupFilesDir, "engine-packs/${BuildConfig.VERSION_CODE}/${engine.key}").apply { mkdirs() }
    fun workName(engine: EngineId) = "engine-pack-${BuildConfig.VERSION_CODE}-${engine.key}"
    fun cachedPlan(context: Context, engine: EngineId) = EnginePackPlan.parse(
        File(directory(context, engine), "manifest.json").readText(), engine, BuildConfig.VERSION_CODE, supportedAbis)
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Unique WorkManager task; bounded downloads, resume, cancellation, disk checks and SHA-256 before install. */
class EngineDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val engine = EngineId.find(inputData.getString("engine")) ?: return@withContext Result.failure()
        if (EngineFeatures.installed(applicationContext, engine)) return@withContext Result.success()
        try {
            setForeground(notification(engine, 0))
            var accepted: Triple<String, EnginePackPlan, String>? = null
            for (source in listOf(EnginePacks.manifestUrl, EnginePacks.fallbackManifestUrl)) {
                try {
                    val raw = connection(source).let { c -> try {
                        c.connectTimeout = 8_000
                        if (c.responseCode != 200) throw IOException("manifest")
                        require(c.url.protocol == "https")
                        c.inputStream.use { BoundedInput.read(it, 65536).toString(Charsets.UTF_8) }
                    } finally { c.disconnect() } }
                    val plan = EnginePackPlan.parse(raw, engine, BuildConfig.VERSION_CODE, EnginePacks.supportedAbis)
                    require(EnginePacks.hash(File(applicationContext.applicationInfo.sourceDir)) in plan.baseHashes)
                    accepted = Triple(raw, plan, source); break
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { currentCoroutineContext().ensureActive() }
            }
            val (raw, plan, source) = accepted ?: throw IOException("manifest_unavailable")
            val directory = EnginePacks.directory(applicationContext, engine)
            val remaining = plan.files.sumOf { f -> (f.bytes - File(directory, f.name + ".part").length().coerceAtMost(f.bytes)).coerceAtLeast(0) }
            require(directory.usableSpace > remaining + plan.bytes + 32L * 1024 * 1024)
            var completed = 0L
            for (file in plan.files) {
                currentCoroutineContext().ensureActive()
                val final = File(directory, file.name)
                if (final.length() != file.bytes || EnginePacks.hash(final) != file.sha256) {
                    if (final.exists()) check(final.delete())
                    val part = File(directory, file.name + ".part")
                    if (part.length() > file.bytes) check(part.delete())
                    if (part.length() < file.bytes) {
                        val c = connection(EnginePackPlan.downloadUrl(source, file))
                        try {
                            val offset = part.length()
                            if (offset > 0) c.setRequestProperty("Range", "bytes=$offset-")
                            val code = c.responseCode
                            require(c.url.protocol == "https")
                            val start = when {
                                code == 206 && ModelDownloadWorker.validRange(c.getHeaderField("Content-Range"), offset, file.bytes) -> offset
                                code == 200 -> 0L
                                else -> throw IOException("response")
                            }
                            RandomAccessFile(part, "rw").use { output ->
                                output.setLength(start); output.seek(start)
                                c.inputStream.buffered().use { input ->
                                    val buffer = ByteArray(128 * 1024); var received = start; var last = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer); if (n < 0) break
                                        require(received + n <= file.bytes)
                                        output.write(buffer, 0, n); received += n
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        if (now - last > 750) {
                                            val percent = ((completed + received) * 100 / plan.bytes).toInt()
                                            setProgress(workDataOf("percent" to percent)); setForeground(notification(engine, percent)); last = now
                                        }
                                    }
                                    require(received == file.bytes); output.fd.sync()
                                }
                            }
                        } finally { c.disconnect() }
                    }
                    currentCoroutineContext().ensureActive()
                    if (part.length() != file.bytes || EnginePacks.hash(part) != file.sha256) {
                        part.delete(); throw IOException("checksum")
                    }
                    check(part.renameTo(final))
                }
                completed += file.bytes
            }
            val receipt = File(directory, "manifest.json.part"); receipt.writeText(raw)
            check(receipt.renameTo(File(directory, "manifest.json")))
            Result.success(workDataOf("ready" to true))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            Result.failure(workDataOf("error" to "download_or_verification_failed"))
        }
    }

    private fun connection(url: String): HttpURLConnection {
        val parsed = URL(url); require(parsed.protocol == "https" && parsed.userInfo == null)
        return (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 20_000; setRequestProperty("Accept-Encoding", "identity")
        }
    }
    private fun notification(engine: EngineId, percent: Int): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("engine-download", engineText("功能组件下载", "Component downloads", "Téléchargement de composants"), NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, "engine-download").setSmallIcon(R.drawable.ic_ai_assistant)
            .setContentTitle(engine.title).setContentText("$percent%").setProgress(100, percent, false).setOngoing(true)
            .addAction(0, engineText("取消", "Cancel", "Annuler"), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(8700 + engine.ordinal, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(8700 + engine.ordinal, n)
    }
}
