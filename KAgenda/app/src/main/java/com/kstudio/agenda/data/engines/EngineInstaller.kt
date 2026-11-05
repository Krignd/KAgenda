package com.kstudio.agenda.data.engines

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.kstudio.agenda.ui.engines.EngineDownloadActivity
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object EngineInstaller {
    /** No arbitrary dex/.so is loaded. The OS checks same package, signing identity and base version. */
    fun install(context: Context, engine: EngineId) {
        check(context.packageManager.canRequestPackageInstalls())
        val plan = EnginePacks.cachedPlan(context, engine)
        check(EnginePacks.hash(File(context.applicationInfo.sourceDir)) in plan.baseHashes)
        val directory = EnginePacks.directory(context, engine)
        val files = plan.files.map { expected ->
            val file = File(directory, expected.name)
            check(file.length() == expected.bytes && EnginePacks.hash(file) == expected.sha256)
            ZipFile(file).use { zip ->
                check(zip.getEntry("AndroidManifest.xml") != null)
                val entry = zip.getEntry("assets/engine-receipts/${engine.key}.json") ?: error("module")
                val text = zip.getInputStream(entry).use { com.kstudio.agenda.data.BoundedInput.read(it, 4096).toString(Charsets.UTF_8) }
                val receipt = JSONObject(text)
                check(receipt.getString("engine") == engine.key && receipt.getString("split") == engine.split)
            }
            file
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING).apply {
            setAppPackageName(context.packageName)
            setSize(plan.bytes)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        context.getSharedPreferences("engine-install", Context.MODE_PRIVATE).edit().remove(engine.key).apply()
        try {
            installer.openSession(sessionId).use { session ->
                for (file in files) {
                    file.inputStream().use { input -> session.openWrite(file.name, 0, file.length()).use { output ->
                        input.copyTo(output); session.fsync(output)
                    } }
                }
                val intent = Intent(context, EngineInstallReceiver::class.java).setAction("${context.packageName}.ENGINE_INSTALL")
                    .putExtra("engine", engine.key)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) { installer.abandonSession(sessionId); throw e }
    }
}

class EngineInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = EngineId.find(intent.getStringExtra("engine")) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            try { context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: RuntimeException) { record(context, engine, PackageInstaller.STATUS_FAILURE) }
        } else {
            record(context, engine, status)
            // No response bodies, URIs or raw system errors are retained in application logs.
        }
    }
    private fun record(context: Context, engine: EngineId, status: Int) {
        context.getSharedPreferences("engine-install", Context.MODE_PRIVATE).edit().putInt(engine.key, status).apply()
    }
}
