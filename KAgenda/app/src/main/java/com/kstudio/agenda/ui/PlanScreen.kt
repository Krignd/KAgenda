@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import java.time.LocalDate

/** 计划页内的三种视图模式 */
private enum class PlanMode { Day, Week, Month }

/**
 * 「计划」页：与「日程表」并列，用于非学校规定的个人计划。
 * 框架与日程表类似：顶部切换 日视图 / 周视图 / 月视图，数据为标记 isPlan 的本地条目。
 */
@Composable
fun PlanScreen(vm: AppViewModel) {
    var modeName by rememberSaveable { mutableStateOf(PlanMode.Day.name) }
    val mode = runCatching { PlanMode.valueOf(modeName) }.getOrDefault(PlanMode.Day)
    val t = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        // 顶部：视图切换（日 / 周 / 月）
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PlanMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { modeName = m.name },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PlanMode.entries.size,
                    ),
                    label = {
                        Text(
                            when (m) {
                                PlanMode.Day -> t.viewDay
                                PlanMode.Week -> t.viewWeek
                                PlanMode.Month -> t.viewMonth
                            }
                        )
                    },
                )
            }
        }
        when (mode) {
            PlanMode.Day -> PlanDayView(vm)
            PlanMode.Week -> PlanWeekView(vm)
            PlanMode.Month -> PlanMonthView(vm, onOpenDay = { date ->
                vm.selectDate(date)
                modeName = PlanMode.Day.name
            })
        }
    }
}

// ---------------------------------------------------------------- 日视图

@Composable
private fun PlanDayView(vm: AppViewModel) {
    val agendaAll by vm.agenda.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val t = LocalStrings.current
    val plans = agendaAll.filter { it.isPlan }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }

    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    Column(Modifier.fillMaxSize()) {
        DayStrip(monday, selected, vm::selectDate)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.selectDate(selected.minusDays(7)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevWeek)
            }
            Text(
                text = rangeLabel(monday),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { vm.selectDate(selected.plusDays(7)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextWeek)
            }
        }

        val dayPlans = plans
            .filter { it.coversDate(selected) }
            .sortedWith(compareBy({ it.startTime.ifBlank { "00:00" } }, { it.dateEpochDay }))
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "plan-header") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.myPlans,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        editing = null
                        editorOpen = true
                    }) { Text(t.addPlanBtn) }
                }
            }
            if (dayPlans.isEmpty()) {
                item(key = "plan-empty") {
                    Text(
                        text = t.emptyPlansHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else {
                items(dayPlans, key = { "plan-" + it.id }) { ev ->
                    AgendaCard(event = ev, onClick = {
                        editing = ev
                        editorOpen = true
                    })
                }
            }
        }
    }

    if (editorOpen) {
        AgendaEditorDialog(
            initial = editing,
            defaultDate = selected,
            asPlan = true,
            onDismiss = { editorOpen = false },
            onSave = { vm.saveAgendaEvent(it); editorOpen = false },
            onDelete = { id -> vm.deleteAgendaEvent(id); editorOpen = false },
        )
    }
}

// ---------------------------------------------------------------- 周视图

@Composable
private fun PlanWeekView(vm: AppViewModel) {
    val agendaAll by vm.agenda.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val t = LocalStrings.current
    val plans = agendaAll.filter { it.isPlan }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }

    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val weekPlans = plans
        .filter { ev -> (0L..6L).any { off -> ev.coversDate(monday.plusDays(off)) } }
        .sortedWith(compareBy({ it.dateEpochDay }, { it.startTime.ifBlank { "00:00" } }))

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.selectDate(selected.minusDays(7)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevWeek)
            }
            Text(rangeLabel(monday), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.selectDate(selected.plusDays(7)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextWeek)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "plan-header") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.myPlansWeek,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        editing = null
                        editorOpen = true
                    }) { Text(t.addPlanBtn) }
                }
            }
            if (weekPlans.isEmpty()) {
                item(key = "plan-empty") {
                    Text(
                        text = t.emptyPlansHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else {
                items(weekPlans, key = { "plan-" + it.id }) { ev ->
                    val d = ev.date
                    AgendaCard(
                        event = ev,
                        dateLabel = "${d.monthValue}/${d.dayOfMonth} ${t.weekdayShort(d.dayOfWeek.value)}",
                        onClick = {
                            editing = ev
                            editorOpen = true
                        },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        AgendaEditorDialog(
            initial = editing,
            defaultDate = selected,
            asPlan = true,
            onDismiss = { editorOpen = false },
            onSave = { vm.saveAgendaEvent(it); editorOpen = false },
            onDelete = { id -> vm.deleteAgendaEvent(id); editorOpen = false },
        )
    }
}

// ---------------------------------------------------------------- 月视图

@Composable
private fun PlanMonthView(vm: AppViewModel, onOpenDay: (LocalDate) -> Unit) {
    val agendaAll by vm.agenda.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val t = LocalStrings.current
    val plans = agendaAll.filter { it.isPlan }

    var monthStart by remember { mutableStateOf(selected.withDayOfMonth(1)) }
    val today = LocalDate.now()

    val planMap = remember(agendaAll, monthStart) {
        plans
            .filter {
                val d = LocalDate.ofEpochDay(it.dateEpochDay)
                d.year == monthStart.year && d.monthValue == monthStart.monthValue
            }
            .groupBy { it.dateEpochDay }
    }

    Column(Modifier.fillMaxSize()) {
        // 月份导航
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { monthStart = monthStart.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevMonth)
            }
            Text(
                text = t.monthTitle(monthStart.year, monthStart.monthValue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { monthStart = monthStart.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextMonth)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { monthStart = today.withDayOfMonth(1) }) { Text(t.backToThisMonth) }
        }

        // 星期表头
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            for (d in 1..7) {
                Text(
                    text = t.weekdayNarrow(d),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // 月历网格（仅计划条目）
        val leading = monthStart.dayOfWeek.value - 1
        val daysInMonth = monthStart.lengthOfMonth()
        val rows = (leading + daysInMonth + 6) / 7
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - leading + 1
                        val date = if (dayNum in 1..daysInMonth) monthStart.withDayOfMonth(dayNum) else null
                        MonthCell(
                            date = date,
                            courses = emptyList(),
                            events = date?.let { planMap[it.toEpochDay()] }.orEmpty(),
                            isToday = date == today,
                            isSelected = date == selected,
                            onClick = { date?.let(onOpenDay) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** "9/14 – 9/20" 形式的周范围 */
private fun rangeLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return "${monday.monthValue}/${monday.dayOfMonth} – ${sunday.monthValue}/${sunday.dayOfMonth}"
}
