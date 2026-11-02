package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.kstudio.agenda.ui.theme.FrostedAlertDialog as AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.kstudio.agenda.ui.components.AppOutlinedButton as OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.kstudio.agenda.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kstudio.agenda.data.AiInference
import com.kstudio.agenda.data.AppSettings
import com.kstudio.agenda.data.LocalAiModel
import com.kstudio.agenda.data.LocalAiModels
import com.kstudio.agenda.data.ModelDownloadWorker
import com.kstudio.agenda.data.ModelFileLocks
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.LocalAiText
import com.kstudio.agenda.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LocalAiSettings(settings: AppSettings, cloudSettings: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val t = LocalStrings.current
    Text(LocalAiText.intro, style = MaterialTheme.typography.bodySmall)
    for ((id, label) in listOf("local" to LocalAiText.local, "cloud" to LocalAiText.cloud)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = settings.aiProvider == id, onClick = { scope.launch { SettingsStore.setAiProvider(context, id) } })
            Text(label)
        }
    }
    if (settings.aiProvider == "local") {
        if (!LocalAiModels.supported()) {
            Text(LocalAiText.unsupported, color = MaterialTheme.colorScheme.error)
        } else {
            for (model in LocalAiModels.all) {
                HorizontalDivider()
                LocalModelRow(model, settings.localAiModel)
            }
            Text(LocalAiText.downloadHint, style = MaterialTheme.typography.bodySmall)
            Text(LocalAiText.source, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Text(LocalAiText.cloudNote, style = MaterialTheme.typography.bodySmall)
        cloudSettings()
    }
}

@Composable
private fun LocalModelRow(model: LocalAiModel, selected: String) {
    val context = LocalContext.current.applicationContext
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { WorkManager.getInstance(context) }
    val infos by remember(manager, model.id) { manager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(model)) }.collectAsState(emptyList())
    val work = infos.firstOrNull()
    val downloading = work != null && !work.state.isFinished
    var installed by remember(model) { mutableStateOf(false) }
    var partialBytes by remember(model) { mutableLongStateOf(0L) }
    var revision by remember(model) { mutableIntStateOf(0) }
    var busy by remember(model) { mutableStateOf(false) }
    var askDownload by remember(model) { mutableStateOf(false) }
    var askDelete by remember(model) { mutableStateOf(false) }
    var wifiOnly by remember(model) { mutableStateOf(true) }
    var actionError by remember(model) { mutableStateOf("") }
    LaunchedEffect(model, work?.state, revision) {
        val state = withContext(Dispatchers.IO) { LocalAiModels.installed(context, model) to LocalAiModels.partial(context, model).length() }
        installed = state.first
        partialBytes = state.second
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.height(4.dp))
        Text(model.name, style = MaterialTheme.typography.titleSmall)
        Text(if (model == LocalAiModels.all.first()) LocalAiText.light else LocalAiText.standard, style = MaterialTheme.typography.bodySmall)
        val status = when {
            installed -> if (selected == model.id) LocalAiText.selected else LocalAiText.ready
            downloading -> if (work!!.progress.getBoolean("verifying", false)) LocalAiText.verifying else LocalAiText.downloading
            partialBytes > 0 -> LocalAiText.partial
            else -> LocalAiText.notDownloaded
        }
        Text("${LocalAiText.downloadSize}：${sizeLabel(model.bytes)} · $status", style = MaterialTheme.typography.bodySmall)
        if (downloading) {
            val received = work!!.progress.getLong("bytes", partialBytes)
            LinearProgressIndicator(progress = { (received.toFloat() / model.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(when {
                work.progress.getBoolean("verifying", false) -> LocalAiText.verifying
                work.state != WorkInfo.State.RUNNING -> LocalAiText.waiting
                else -> "${sizeLabel(received)} / ${sizeLabel(model.bytes)} · ${received * 100 / model.bytes}%"
            }, style = MaterialTheme.typography.bodySmall)
        }
        if (!downloading && !installed && work?.state == WorkInfo.State.FAILED)
            Text(LocalAiText.error(work.outputData.getString("error").orEmpty()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (actionError.isNotEmpty()) Text(actionError, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                downloading -> TextButton(onClick = { manager.cancelUniqueWork(ModelDownloadWorker.workName(model)) }) { Text(LocalAiText.cancel) }
                installed -> TextButton(
                    enabled = selected != model.id && !busy,
                    onClick = { scope.launch { SettingsStore.setLocalAiModel(context, model.id) } },
                ) { Text(if (selected == model.id) LocalAiText.selected else LocalAiText.use) }
                else -> OutlinedButton(onClick = { askDownload = true }, enabled = !busy) { Text(if (partialBytes > 0) LocalAiText.resume else LocalAiText.download) }
            }
            if (!downloading && (installed || partialBytes > 0)) TextButton(onClick = { askDelete = true }, enabled = !busy) { Text(if (installed) LocalAiText.delete else LocalAiText.removePartial) }
        }
    }
    if (askDownload) AlertDialog(
        onDismissRequest = { askDownload = false },
        title = { Text(LocalAiText.download) },
        text = { Column {
            Text("${model.name}\n${LocalAiText.downloadSize}：${sizeLabel(model.bytes)}\n${LocalAiText.source}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                Text(LocalAiText.wifiOnly)
            }
        } },
        confirmButton = { TextButton(onClick = {
            askDownload = false
            actionError = ""
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf("model" to model.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniqueWork(ModelDownloadWorker.workName(model), ExistingWorkPolicy.KEEP, request)
        }) { Text(LocalAiText.download) } },
        dismissButton = { TextButton(onClick = { askDownload = false }) { Text(t.cancel) } },
    )
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text(if (installed) LocalAiText.delete else LocalAiText.removePartial) },
        text = { Text(model.name + "\n" + LocalAiText.text("删除后可重新下载，日程数据会保留。", "You can download it again. Your events are kept.", "Vous pourrez le télécharger à nouveau. Vos événements sont conservés.")) },
        confirmButton = { TextButton(onClick = {
            askDelete = false
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        manager.cancelUniqueWork(ModelDownloadWorker.workName(model)).result.get()
                        AiInference.modelMutex.withLock {
                            ModelFileLocks.forModel(model.id).withLock {
                                for (file in listOf(LocalAiModels.receipt(context, model), LocalAiModels.file(context, model), LocalAiModels.partial(context, model))) {
                                    check(!file.exists() || file.delete())
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    actionError = LocalAiText.text("删除失败，请稍后重试", "Could not delete. Retry later.", "Suppression impossible. Réessayez.")
                } finally { busy = false; revision++ }
            }
        }) { Text(if (installed) LocalAiText.delete else LocalAiText.removePartial) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(t.cancel) } },
    )
}

private fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000_000)
    String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0)
else String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
