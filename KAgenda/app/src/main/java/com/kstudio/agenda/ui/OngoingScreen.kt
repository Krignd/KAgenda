package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.AppStrings
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.ui.components.EmptyState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 「进行中」页：专门展示正在进行的长日程 / 长计划（跨天时间段，如考试报名）。
 * 按结束时间升序排列，显示剩余时间；点击卡片可编辑/删除。
 */
@Composable
fun OngoingScreen(vm: AppViewModel) {
    val agenda by vm.agenda.collectAsState()
    val t = LocalStrings.current
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val now = LocalDateTime.now()
    val ongoing = agenda
        .filter { it.isLong && it.isOngoing(now) }
        .sortedBy { it.endDateTime() }

    if (ongoing.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            EmptyState(
                title = t.ongoingEmptyTitle,
                hint = t.ongoingEmptyHint,
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Text(
                    text = t.ongoingHeader(ongoing.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ongoing, key = { it.id }) { ev ->
                AgendaCard(
                    event = ev,
                    extraLine = remainingLabel(ev.endDateTime(), now, t),
                    onClick = {
                        editing = ev
                        editorOpen = true
                    },
                )
            }
        }
    }

    if (editorOpen) {
        AgendaEditorDialog(
            initial = editing,
            defaultDate = LocalDate.now(),
            onDismiss = { editorOpen = false },
            onSave = { vm.saveAgendaEvent(it); editorOpen = false },
            onDelete = { id -> vm.deleteAgendaEvent(id); editorOpen = false },
        )
    }
}

/** 剩余时间文案："剩余 3天4小时" / "3d 4h left" / "Reste 3 j 4 h" */
private fun remainingLabel(end: LocalDateTime, now: LocalDateTime, t: AppStrings): String {
    val minutes = Duration.between(now, end).toMinutes().coerceAtLeast(0)
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val mins = minutes % 60
    return t.remaining(days, hours, mins)
}
