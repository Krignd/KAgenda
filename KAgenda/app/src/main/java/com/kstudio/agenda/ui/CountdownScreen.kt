package com.kstudio.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.Deadline
import com.kstudio.agenda.data.PlannerStore
import com.kstudio.agenda.data.asDeadline
import com.kstudio.agenda.data.countdowns
import com.kstudio.agenda.ui.components.AppOutlinedButton
import com.kstudio.agenda.ui.components.cardBaseColor
import com.kstudio.agenda.ui.components.cardGlassBorder
import com.kstudio.agenda.ui.components.cardShadowElevation
import com.kstudio.agenda.ui.components.cardTonalElevation
import com.kstudio.agenda.ui.theme.FrostedSurface
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannerMenu(label: String, choices: Map<String, String>, selected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppOutlinedButton(onClick = { open = true }) {
            Text(label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
        ) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title, color = MaterialTheme.colorScheme.onSurface) }, onClick = {
                    open = false
                    selected(key)
                })
            }
        }
    }
}

@Composable
internal fun CountdownScreen(vm: AppViewModel) {
    val data by PlannerStore.data.collectAsState()
    val events by vm.agenda.collectAsState()
    val semester by vm.semester.collectAsState()
    val settings by vm.settings.collectAsState()
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(1_000)
        }
    }
    val today = now.toLocalDate()
    val periodOptions = linkedMapOf(
        "all" to plannerText("全部", "All", "Tout"),
        "month" to plannerText("当前月", "This month", "Ce mois"),
        "week" to plannerText("当前周", "This week", "Cette semaine"),
        "day" to plannerText("今日", "Today", "Aujourd’hui"),
    )
    val formatOptions = linkedMapOf(
        "auto" to plannerText("自动（24 小时起显示天）", "Automatic (days from 24 hours)", "Automatique (jours dès 24 h)"),
        "days_clock" to plannerText("天 + 时:分:秒", "Days + hh:mm:ss", "Jours + hh:mm:ss"),
        "hours" to plannerText("累计时:分:秒", "Total hh:mm:ss", "Total hh:mm:ss"),
    )
    val notesById = remember(data.notes) { data.notes.associateBy { it.id } }
    val openNote = LocalOpenLessonNote.current
    val counters = remember(data, events, semester, settings.schoolId, today) {
        data.countdowns(events, semester, settings.schoolId)
            .filter { includesCountdownDate(data.countdownPeriod, today, it.at.toLocalDate()) }.sortedBy { it.at }
    }
    val deadlines = remember(data, today) {
        data.notes.filterNot { it.countdown }.mapNotNull { it.asDeadline() }
            .filter { includesCountdownDate(data.countdownPeriod, today, it.at.toLocalDate()) }.sortedBy { it.at }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlannerMenu(periodOptions.getValue(data.countdownPeriod), periodOptions) { period ->
                    vm.editPlanner { it.copy(countdownPeriod = period) }
                }
                PlannerMenu(plannerText("显示格式", "Display format", "Format"), formatOptions) { format ->
                    vm.editPlanner { it.copy(countdownFormat = format) }
                }
            }
        }
        item(key = "counter-header") {
            CountdownSection(plannerText("倒计时", "Countdowns", "Comptes à rebours"), counters.size, data.countdownsExpanded) {
                vm.editPlanner { it.copy(countdownsExpanded = !it.countdownsExpanded) }
            }
        }
        if (data.countdownsExpanded) {
            if (counters.isEmpty()) item { CountdownEmpty(plannerText("暂无倒计时", "No countdowns", "Aucun compte à rebours")) }
            items(counters, key = { "counter/${it.id}" }) { item ->
                val seconds = Duration.between(now, item.at.atZone(now.zone)).seconds
                val note = notesById[item.id]
                DeadlineCard(item, countdownLabel(seconds, data.countdownFormat), open = note?.let { { openNote(it.lesson, it.id) } }) {
                    vm.editPlanner { state ->
                        state.copy(links = state.links.filterNot { it.id == item.id },
                            notes = state.notes.map { if (it.id == item.id) it.copy(countdown = false) else it })
                    }
                }
            }
        }
        item(key = "deadlines-header") {
            CountdownSection(plannerText("截止时间", "Deadlines", "Échéances"), deadlines.size, data.deadlinesExpanded) {
                vm.editPlanner { it.copy(deadlinesExpanded = !it.deadlinesExpanded) }
            }
        }
        if (data.deadlinesExpanded) {
            if (deadlines.isEmpty()) item { CountdownEmpty(plannerText("暂无截止时间", "No deadlines", "Aucune échéance")) }
            items(deadlines, key = { "deadline/${it.id}" }) { item ->
                val note = notesById.getValue(item.id)
                DeadlineCard(item, open = { openNote(note.lesson, note.id) })
            }
        }
    }
}

internal fun includesCountdownDate(period: String, now: LocalDate, date: LocalDate): Boolean = when (period) {
    "day" -> date == now
    "week" -> date.with(DayOfWeek.MONDAY) == now.with(DayOfWeek.MONDAY)
    "month" -> YearMonth.from(date) == YearMonth.from(now)
    else -> true
}

internal fun countdownLabel(seconds: Long, format: String): String {
    if (seconds <= 0) return plannerText("已到时间", "Time reached", "Échéance atteinte")
    val days = seconds / 86_400
    val hours = seconds / 3_600
    val clock = String.format(Locale.ROOT, "%02d:%02d:%02d", if (format == "hours") hours else hours % 24,
        seconds / 60 % 60, seconds % 60)
    val dayText = plannerText("$days 天", "$days d", "$days j")
    return when {
        format == "days_clock" -> "$dayText\n$clock"
        format == "auto" && days > 0 -> dayText
        else -> clock
    }
}

@Composable
private fun CountdownEmpty(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CountdownSection(title: String, size: Int, expanded: Boolean, toggle: () -> Unit) {
    PlannerCard(onClick = toggle) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) plannerText("折叠", "Collapse", "Réduire") else plannerText("展开", "Expand", "Développer"))
        }
    }
}

private val deadlineTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

@Composable
private fun DeadlineCard(item: Deadline, remaining: String? = null, open: (() -> Unit)? = null, remove: (() -> Unit)? = null) {
    PlannerCard(onClick = open) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.at.format(deadlineTimeFormat), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (remove != null) TextButton(onClick = remove, contentPadding = PaddingValues(0.dp)) {
                    Text(plannerText("移除倒计时", "Remove countdown", "Retirer le compte à rebours"))
                }
            }
            if (remaining != null) Text(remaining, Modifier.widthIn(max = 146.dp), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun PlannerCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    FrostedSurface(
        modifier = Modifier.fillMaxWidth().cardGlassBorder(shape)
            .then(if (onClick == null) Modifier else Modifier.clip(shape).clickable(onClick = onClick)),
        shape = shape, color = cardBaseColor(), contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = cardTonalElevation(), shadowElevation = cardShadowElevation(), content = content,
    )
}
