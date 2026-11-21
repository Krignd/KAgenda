package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.kstudio.agenda.ui.theme.FrostedAlertDialog as AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.CourseSyncReview
import com.kstudio.agenda.data.SyncedLesson
import com.kstudio.agenda.data.UpdateInfo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateOfferSheet(info: UpdateInfo, onIgnore: () -> Unit, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val date = runCatching {
        OffsetDateTime.parse(info.publishedAt).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.recoverCatching { LocalDate.parse(info.publishedAt).toString() }.getOrNull()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 1f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plannerText("发现新版本", "Update available", "Mise à jour disponible"), Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(info.versionName, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(date ?: plannerText("未提供发布时间", "Release date unavailable", "Date de publication indisponible"),
                Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(info.notes.ifBlank { plannerText("未提供更新说明", "No release notes", "Aucune note de version") },
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onIgnore, modifier = Modifier.weight(1f)) {
                    Text(plannerText("忽略此版本", "Skip version", "Ignorer"))
                }
                Button(onClick = onUpdate, modifier = Modifier.weight(1f)) {
                    Text(plannerText("更新", "Update", "Mettre à jour"))
                }
            }
        }
    }
}

@Composable
internal fun CourseSyncDialog(review: CourseSyncReview, onApply: () -> Unit, onKeep: () -> Unit) {
    fun label(row: SyncedLesson) = listOf(row.date.toString(), row.time, row.title, row.room, row.teacher)
        .filter { it.isNotBlank() }.joinToString(" · ")
    AlertDialog(
        onDismissRequest = {},
        title = { Text(plannerText("检测到课程变动", "Timetable changes detected", "Modifications des cours détectées")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.semesterChanged) item {
                    Text(plannerText("教务学期或第一教学周已变化", "The term or its first teaching week changed", "Le semestre ou sa première semaine a changé"))
                }
                if (review.removed.isNotEmpty()) {
                    item { Text(plannerText("原安排（${review.removed.size} 节）", "Previous entries (${review.removed.size})",
                        "Anciens cours (${review.removed.size})"), style = MaterialTheme.typography.titleSmall) }
                    items(review.removed) { Text(label(it)) }
                }
                if (review.added.isNotEmpty()) {
                    item { Text(plannerText("新安排（${review.added.size} 节）", "New entries (${review.added.size})",
                        "Nouveaux cours (${review.added.size})"), style = MaterialTheme.typography.titleSmall) }
                    items(review.added) { Text(label(it)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(plannerText("应用教务变动", "Apply changes", "Appliquer")) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(plannerText("保留当前课表", "Keep current", "Conserver")) } },
    )
}
