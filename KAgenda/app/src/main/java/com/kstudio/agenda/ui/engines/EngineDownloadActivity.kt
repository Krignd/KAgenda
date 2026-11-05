package com.kstudio.agenda.ui.engines

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kstudio.agenda.data.engines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A user-initiated component install. No account or imported document is sent to the download host. */
class EngineDownloadActivity : ComponentActivity() {
    private lateinit var engine: EngineId
    private lateinit var status: TextView
    private lateinit var download: Button
    private lateinit var install: Button
    private lateinit var cancel: Button
    private lateinit var progress: ProgressBar
    private var ready = false
    private var running = false
    private var installing = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = EngineId.find(intent.getStringExtra("engine")) ?: run { finish(); return }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding * 2, padding, padding) }
        fun text(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setPadding(0, padding / 2, 0, padding / 2); layout.addView(this) }
        text(engine.title, 23f)
        text(engineText("首次使用需要下载并由系统确认安装。之后可离线使用，无需谷歌服务。组件属于本应用，不会增加桌面图标。安装可能重启应用，请先保存正在编辑的内容。", "Download once, then confirm installation in Android. Works offline without Google services. No additional launcher icon. Installation may restart the app; save pending edits first.", "Téléchargez une fois, puis confirmez l’installation Android. Utilisable hors ligne sans services Google et sans nouvelle icône. Enregistrez vos modifications : l’application peut redémarrer."))
        status = text("")
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; layout.addView(this) }
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; layout.addView(this) }
        download = button(engineText("下载 / 继续下载", "Download / resume", "Télécharger / reprendre")) {
            val work = OneTimeWorkRequestBuilder<EngineDownloadWorker>().setInputData(workDataOf("engine" to engine.key)).build()
            WorkManager.getInstance(this).enqueueUniqueWork(EnginePacks.workName(engine), ExistingWorkPolicy.KEEP, work)
        }
        install = button(engineText("确认安装组件", "Install component", "Installer le composant")) {
            if (!packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else {
                installing = true; refresh()
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { EngineInstaller.install(this@EngineDownloadActivity, engine) }
                        status.text = engineText("请在系统页面确认；安装完成后重新打开应用，再使用此功能。", "Confirm in Android, then reopen the app to use this feature.", "Confirmez dans Android, puis rouvrez l’application.")
                    } catch (_: Exception) {
                        status.text = engineText("安装未完成，请重试；校验不通过时请重新下载。", "Installation did not finish. Retry or download again if verification fails.", "Installation inachevée. Réessayez ou retéléchargez si la vérification échoue.")
                    } finally { installing = false; refreshButtons() }
                }
            }
        }
        cancel = button(engineText("取消下载", "Cancel download", "Annuler le téléchargement")) { WorkManager.getInstance(this).cancelUniqueWork(EnginePacks.workName(engine)) }
        button(engineText("返回", "Back", "Retour")) { finish() }
        setContentView(layout)
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(EnginePacks.workName(engine)).observe(this) { rows ->
            val work = rows.firstOrNull(); running = work != null && !work.state.isFinished
            progress.progress = work?.progress?.getInt("percent", 0) ?: 0
            if (work?.state == WorkInfo.State.FAILED) status.text = engineText("下载或校验失败。组件可能尚未发布，或网络不可用；可稍后重试。", "Download or verification failed. This component may not be published yet, or the network is unavailable. Retry later.", "Échec du téléchargement ou de la vérification. Le composant peut être indisponible. Réessayez plus tard.")
            else refresh()
            refreshButtons()
        }
        refresh()
    }
    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
    private fun refresh() {
        ready = runCatching {
            val plan = EnginePacks.cachedPlan(this, engine)
            plan.files.all { java.io.File(EnginePacks.directory(this, engine), it.name).length() == it.bytes }
        }.getOrDefault(false)
        status.text = when {
            EngineFeatures.installed(this, engine) -> engineText("组件已安装。请返回重试；必要时重新打开应用。", "Installed. Retry the feature; reopen the app if needed.", "Installé. Réessayez ; rouvrez l’application si nécessaire.")
            getSharedPreferences("engine-install", Context.MODE_PRIVATE).getInt(engine.key, 0) > 0 -> engineText("上次安装已取消或失败。可重试；如果系统提示签名或版本不匹配，请下载匹配当前应用的组件。", "Installation was cancelled or failed. Retry; if Android reports a signature or version mismatch, use components matching this app.", "Installation annulée ou échouée. Réessayez avec un composant correspondant à cette application.")
            running -> engineText("正在下载，可离开此页面。", "Downloading. You may leave this page.", "Téléchargement en cours. Vous pouvez quitter cette page.")
            ready -> engineText("下载完成，等待系统安装确认。", "Downloaded. Ready for Android installation.", "Téléchargé. Prêt à installer.")
            else -> engineText("仅首次使用需要联网下载组件。", "Internet is required only for the initial component download.", "Internet est nécessaire uniquement au premier téléchargement.")
        }
        refreshButtons()
    }
    private fun refreshButtons() {
        val installed = EngineFeatures.installed(this, engine)
        download.isEnabled = !installed && !running && !installing
        install.isEnabled = !installed && ready && !running && !installing
        cancel.isEnabled = running
    }
    companion object {
        fun open(context: Context, engine: EngineId) {
            context.startActivity(Intent(context, EngineDownloadActivity::class.java).putExtra("engine", engine.key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
