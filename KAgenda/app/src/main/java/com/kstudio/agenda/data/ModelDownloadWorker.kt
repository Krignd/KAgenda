package com.kstudio.agenda.data

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
import com.kstudio.agenda.R
import com.kstudio.agenda.i18n.LocalAiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Unique work survives screen closure and process recreation. Partial downloads are resumable. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = LocalAiModels.find(inputData.getString("model").orEmpty()) ?: return@withContext Result.failure()
        ModelFileLocks.forModel(model.id).withLock {
            if (!LocalAiModels.supported()) return@withContext Result.failure(workDataOf("error" to "unsupported"))
            if (LocalAiModels.installed(applicationContext, model)) return@withContext Result.success()
            try {
                setForeground(notification(model, 0))
                val part = LocalAiModels.partial(applicationContext, model)
                if (part.length() > model.bytes) part.delete()
                if (part.parentFile!!.usableSpace < model.bytes - part.length() + 128L * 1024 * 1024)
                    return@withContext Result.failure(workDataOf("error" to "space"))
                if (part.length() < model.bytes) download(model)
                setProgress(workDataOf("bytes" to model.bytes, "verifying" to true))
                val digest = MessageDigest.getInstance("SHA-256")
                part.inputStream().buffered().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (part.length() != model.bytes || hash != model.sha256) {
                    part.delete()
                    return@withContext Result.failure(workDataOf("error" to "checksum"))
                }
                currentCoroutineContext().ensureActive()
                if (!part.renameTo(LocalAiModels.file(applicationContext, model))) throw IOException("rename")
                LocalAiModels.receipt(applicationContext, model).writeText(model.sha256)
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never expose signed redirect URLs, network response bodies or tokens in UI/logs.
                Result.failure(workDataOf("error" to "network"))
            }
        }
    }

    private suspend fun download(model: LocalAiModel) {
        val part = LocalAiModels.partial(applicationContext, model)
        val offset = part.length()
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            val start = when {
                code == 206 && validRange(connection.getHeaderField("Content-Range"), offset, model.bytes) -> offset
                code == 200 -> 0L // Server ignored Range: replace the partial file, never append it.
                else -> throw IOException("HTTP $code")
            }
            RandomAccessFile(part, "rw").use { output ->
                output.setLength(start)
                output.seek(start)
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var received = start
                    var lastUpdate = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (received + n > model.bytes) throw IOException("oversize")
                        output.write(buffer, 0, n)
                        received += n
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 1000) {
                            setProgress(workDataOf("bytes" to received))
                            setForeground(notification(model, (received * 100 / model.bytes).toInt()))
                            lastUpdate = now
                        }
                    }
                    if (received != model.bytes) throw IOException("incomplete")
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun notification(model: LocalAiModel, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("local-model-download", LocalAiText.title, NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "local-model-download")
            .setSmallIcon(R.drawable.ic_ai_assistant)
            .setContentTitle(LocalAiText.title)
            .setContentText("${model.name} · $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .addAction(0, LocalAiText.cancel, WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(7100 + LocalAiModels.all.indexOf(model), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(7100 + LocalAiModels.all.indexOf(model), notification)
    }

    companion object {
        fun workName(model: LocalAiModel) = "local-ai-download-${model.id}"
        internal fun validRange(value: String?, offset: Long, total: Long): Boolean {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value.orEmpty()) ?: return false
            return match.groupValues[1].toLongOrNull() == offset &&
                match.groupValues[2].toLongOrNull() == total - 1 && match.groupValues[3].toLongOrNull() == total
        }
    }
}
