package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.ui.components.rememberFlashPulse
import com.kstudio.agenda.ui.components.swipeStep
import java.time.LocalDate

/**
 * 月视图：一个月历网格。
 * - 每格显示当天课程/日程的迷你条目（最多 3 条，超出显示 +N）；日程条目带时间标记（HH:mm）；
 * - 点击任意日期跳转到日视图查看详情（与日视图的选中日期联动）。
 */
@Composable
fun MonthScreen(vm: AppViewModel, onOpenDay: (LocalDate) -> Unit, flash: FocusRequest? = null) {
    val semester by vm.semester.collectAsState()
    val agendaAll by vm.agenda.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val t = LocalStrings.current

    // 当前显示的月份（进入本页时对齐到选中日期所在月）
    var monthStart by remember { mutableStateOf(selected.withDayOfMonth(1)) }
    val today = LocalDate.now()

    // 月份导航边界：当前年份往前 4 年的 1 月 ~ 往后 4 年的 12 月
    val monthMin = remember { LocalDate.of(today.year - 4, 1, 1) }
    val monthMax = remember { LocalDate.of(today.year + 4, 12, 1) }
    val shiftMonth: (Int) -> Unit = { delta ->
        val target = monthStart.plusMonths(delta.toLong())
        if (!target.isBefore(monthMin) && !target.isAfter(monthMax)) monthStart = target
    }

    // 闪烁反馈目标（通知/小组件定位进入）：跳到对应月份并闪烁该日期
    val flashDate = flash?.let { LocalDate.ofEpochDay(it.epochDay) }
    LaunchedEffect(flash?.seq) {
        flashDate?.let { d ->
            val target = d.withDayOfMonth(1)
            if (!target.isBefore(monthMin) && !target.isAfter(monthMax)) monthStart = target
        }
    }

    val courseMap = remember(semester, monthStart) { buildCourseMap(semester, monthStart) }
    val agendaMap = remember(agendaAll, monthStart) {
        agendaAll
            .filter { !it.isPlan }
            .filter {
                val d = LocalDate.ofEpochDay(it.dateEpochDay)
                d.year == monthStart.year && d.monthValue == monthStart.monthValue
            }
            .groupBy { it.dateEpochDay }
    }

    Column(
        Modifier
            .fillMaxSize()
            .swipeStep(key = "month", onStep = shiftMonth),
    ) {
        // ---------- 月份导航 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { shiftMonth(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevMonth)
            }
            Text(
                text = t.monthTitle(monthStart.year, monthStart.monthValue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { shiftMonth(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextMonth)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { monthStart = today.withDayOfMonth(1) }) { Text(t.backToThisMonth) }
            // 保存月课表到相册（只显示图标）
            IconButton(onClick = { vm.saveMonthImage(monthStart) }) {
                Icon(Icons.Filled.Save, contentDescription = t.saveMonthSchedule)
            }
        }

        // ---------- 星期表头 ----------
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

        // ---------- 月历网格 ----------
        val leading = monthStart.dayOfWeek.value - 1        // 周一为第一列
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
                            courses = date?.let { courseMap[it.toEpochDay()] }.orEmpty(),
                            events = date?.let { agendaMap[it.toEpochDay()] }.orEmpty(),
                            isToday = date == today,
                            isSelected = date == selected,
                            flash = date != null && date == flashDate,
                            onClick = { date?.let(onOpenDay) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 月历格子（日程条目带 HH:mm 时间标记）；「计划」页复用本组件；[flash] 为定位闪烁反馈 */
@Composable
internal fun MonthCell(
    date: LocalDate?,
    courses: List<Course>,
    events: List<AgendaEvent>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flash: Boolean = false,
) {
    val bg = when {
        date == null -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        // 周末底色轻微加深
        date.dayOfWeek.value >= 6 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val pulse = rememberFlashPulse(flash)
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            // 定位闪烁：底色深浅变化提醒（三下）
            .then(
                if (flash) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f + 0.34f * pulse),
                    RoundedCornerShape(10.dp),
                ) else Modifier
            )
            .then(
                if (flash) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * pulse),
                    RoundedCornerShape(10.dp),
                ) else Modifier
            )
            .then(if (date != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        if (date != null) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    HolidayTable.nameOf(date) != null -> MaterialTheme.colorScheme.error
                    date.dayOfWeek.value >= 6 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val agendaDefault = MaterialTheme.colorScheme.tertiary
            val entries = mutableListOf<Pair<String, Color>>()
            for (course in courses) entries.add(course.title to Color(CoursePalette.colorFor(course)))
            for (ev in events) {
                val color = if (ev.displayColor != 0) Color(ev.displayColor) else agendaDefault
                // 时间标记：有开始时间时以 "HH:mm 标题" 展示
                val label = if (ev.startTime.isNotBlank()) "${ev.startTime} ${ev.title}" else ev.title
                entries.add(label to color)
            }

            val maxShown = 3
            for ((title, color) in entries.take(maxShown)) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.22f))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entries.size > maxShown) {
                Text(
                    text = "+${entries.size - maxShown}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 计算某个月每天的教学课程（按教学周+星期匹配） */
private fun buildCourseMap(sem: SemesterSchedule?, monthStart: LocalDate): Map<Long, List<Course>> {
    if (sem == null) return emptyMap()
    val map = LinkedHashMap<Long, List<Course>>()
    val days = monthStart.lengthOfMonth()
    for (d in 1..days) {
        val date = monthStart.withDayOfMonth(d)
        val weekNo = sem.teachingWeekOf(date)
        val weekCourses = sem.weeks[weekNo] ?: continue
        val list = weekCourses
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
            .sortedBy { it.startPeriod }
        if (list.isNotEmpty()) map[date.toEpochDay()] = list
    }
    return map
}
