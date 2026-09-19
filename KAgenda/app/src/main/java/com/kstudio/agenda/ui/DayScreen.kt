package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.FuzzyTime
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.WeekSchedule
import com.kstudio.agenda.ui.components.EmptyState
import com.kstudio.agenda.ui.components.StatusBanner
import com.kstudio.agenda.ui.components.TagChip
import com.kstudio.agenda.ui.components.rememberFlashPulse
import com.kstudio.agenda.ui.components.swipeStep
import java.time.LocalDate
import java.time.LocalTime

/** 判断课程此刻是否正在进行 */
internal fun isOngoing(course: Course, date: LocalDate): Boolean {
    if (date != LocalDate.now()) return false
    val now = LocalTime.now()
    return !now.isBefore(PeriodTimes.startOf(course.startPeriod)) &&
        now.isBefore(PeriodTimes.endOf(course.endPeriod))
}

/**
 * 日视图。
 * - 课程表模式：课程列表在上，本地日程单独成区；
 * - 非课程表模式：课程与自建日程按开始时间混合排列。
 */
@Composable
fun DayScreen(vm: AppViewModel, timetableMode: Boolean, flash: FocusRequest? = null) {
    val week by vm.weekSchedule.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val sync by vm.syncState.collectAsState()
    val agendaAll by vm.agenda.collectAsState()
    val t = LocalStrings.current

    // 日程新建/编辑对话框状态
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }

    val currentWeek = week
    if (currentWeek == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            SyncHint(sync)
            EmptyState(
                title = t.noScheduleTitle,
                hint = t.noScheduleHintDay,
            )
        }
        return
    }

    // 日程表页只展示「日程」（「计划」页的个人计划不在此混排）
    val agenda = agendaAll.filter { !it.isPlan }

    // 闪烁反馈目标（通知/小组件定位进入）：日期始终闪烁，标题命中时对应卡片一并闪烁
    val flashDate = flash?.let { LocalDate.ofEpochDay(it.epochDay) }
    val flashTitle = flash?.title.orEmpty()
    val matchesFlash: (String) -> Boolean = { title ->
        flashDate == selected && flashTitle.isNotBlank() && title.trim() == flashTitle
    }

    // 外层不加滑动手势：在日期条 / 周导航上滑动不应切换日期（手势只作用于下方内容列表）
    Column(Modifier.fillMaxSize()) {
        DayStrip(currentWeek.monday, selected, vm::selectDate, flashDate = flashDate)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.stepWeek(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevWeek)
            }
            Text(
                text = buildString {
                    append(t.weekNo(currentWeek.weekNo))
                    if (currentWeek.weekRangeLabel.isNotBlank()) append("（${currentWeek.weekRangeLabel}）")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { vm.stepWeek(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextWeek)
            }
            Spacer(Modifier.weight(1f))
            // 只显示保存图标（不显示文字）
            IconButton(onClick = { vm.saveDayImage(selected) }) {
                Icon(Icons.Filled.Save, contentDescription = t.saveDaySchedule)
            }
        }

        val courses = currentWeek.coursesOnDate(selected)
        // 显示覆盖当天的所有日程（含跨天长日程），按时间排序
        val dayEvents = agenda
            .filter { it.coversDate(selected) }
            .sortedWith(compareBy({ FuzzyTime.sortKey(it.startTime) }, { it.dateEpochDay }))

        if (timetableMode) {
            LazyColumn(
                modifier = Modifier.weight(1f).swipeStep(key = "day", onStep = { vm.stepDay(it) }),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (courses.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = t.noCoursesToday,
                            hint = t.noCoursesTodayHint,
                        )
                    }
                } else {
                    items(courses, key = { it.id }) { course ->
                        CourseCard(
                            course = course,
                            highlight = isOngoing(course, selected),
                            flash = matchesFlash(course.title),
                        )
                    }
                }

                // ---------------- 我的日程（本地自建，可增删改） ----------------
                item(key = "agenda-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t.myAgenda,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            editing = null
                            editorOpen = true
                        }) { Text(t.addAgendaBtn) }
                    }
                }
                if (dayEvents.isEmpty()) {
                    item(key = "agenda-empty") {
                        Text(
                            text = t.emptyAgendaHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                } else {
                    items(dayEvents, key = { "agenda-" + it.id }) { ev ->
                        AgendaCard(event = ev, flash = matchesFlash(ev.title), onClick = {
                            editing = ev
                            editorOpen = true
                        })
                    }
                }
            }
        } else {
            // ---------------- 混合模式：课程与日程按时间混排 ----------------
            val merged = mergeDayItems(courses, dayEvents)
            LazyColumn(
                modifier = Modifier.weight(1f).swipeStep(key = "day", onStep = { vm.stepDay(it) }),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "mixed-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t.mixedTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            editing = null
                            editorOpen = true
                        }) { Text(t.addAgendaBtn) }
                    }
                }
                if (merged.isEmpty()) {
                    item(key = "mixed-empty") {
                        EmptyState(title = t.noCoursesToday, hint = t.noCoursesTodayHint)
                    }
                } else {
                    items(merged, key = { item ->
                        when (item) {
                            is DayItem.CourseItem -> "c-" + item.course.id
                            is DayItem.EventItem -> "e-" + item.event.id
                        }
                    }) { item ->
                        when (item) {
                            is DayItem.CourseItem -> CourseCard(
                                course = item.course,
                                highlight = isOngoing(item.course, selected),
                                flash = matchesFlash(item.course.title),
                            )
                            is DayItem.EventItem -> AgendaCard(
                                event = item.event,
                                flash = matchesFlash(item.event.title),
                                onClick = {
                                    editing = item.event
                                    editorOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        AgendaEditorDialog(
            initial = editing,
            defaultDate = selected,
            onDismiss = { editorOpen = false },
            onSave = { vm.saveAgendaEvent(it); editorOpen = false },
            onDelete = { id -> vm.deleteAgendaEvent(id); editorOpen = false },
        )
    }
}

/** 混合模式条目：课程或日程，统一按开始时间排序 */
private sealed interface DayItem {
    val sortMinutes: Int

    data class CourseItem(val course: Course, override val sortMinutes: Int) : DayItem

    data class EventItem(val event: AgendaEvent, override val sortMinutes: Int) : DayItem
}

private fun mergeDayItems(courses: List<Course>, events: List<AgendaEvent>): List<DayItem> {
    val items = mutableListOf<DayItem>()
    for (c in courses) {
        items.add(DayItem.CourseItem(c, PeriodTimes.startOf(c.startPeriod).toSecondOfDay() / 60))
    }
    for (e in events) {
        items.add(DayItem.EventItem(e, FuzzyTime.sortKey(e.startTime).coerceAtLeast(0)))
    }
    return items.sortedWith(
        compareBy(
            { it.sortMinutes },
            { if (it is DayItem.CourseItem) 0 else 1 },
        )
    )
}

/** 一周 7 个日期框（等宽），供日视图与「计划」页共用；[flashDate] 命中时边框闪烁 */
@Composable
internal fun DayStrip(
    monday: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    flashDate: LocalDate? = null,
) {
    val today = LocalDate.now()
    val t = LocalStrings.current
    // 7 个日期框等宽排列（用 weight(1f)，不再横向滚动，宽度完全一致）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (d in 1..7) {
            val date = monday.plusDays((d - 1).toLong())
            val isSelected = date == selected
            val isToday = date == today
            val holiday = HolidayTable.nameOf(date)
            val isWeekend = date.dayOfWeek.value >= 6
            val bg = when {
                isSelected -> Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                )
                isToday -> Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)
                )
                // 周末底色轻微加深（法定节假日由下方文字红色标注）
                isWeekend -> Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    )
                )
                else -> Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
                )
            }
            val fg = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            val flashing = flashDate == date
            val pulse = rememberFlashPulse(flashing)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    // 定位闪烁：底色深浅变化提醒（三下）
                    .then(
                        if (flashing) Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.42f * pulse),
                            RoundedCornerShape(14.dp),
                        ) else Modifier
                    )
                    .then(
                        if (flashing) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * pulse),
                            RoundedCornerShape(14.dp),
                        ) else Modifier
                    )
                    .clickable { onSelect(date) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = t.weekdayShort(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                Text(
                    text = "${date.monthValue}/${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                )
                Text(
                    text = holiday ?: if (isToday) t.today else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (holiday != null && !isSelected) MaterialTheme.colorScheme.error
                    else fg.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    highlight: Boolean = false,
    flash: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val pulse = rememberFlashPulse(flash)
    // CoursePalette 返回 Android 原生 ARGB Int，这里转换为 Compose Color
    val color = Color(CoursePalette.colorFor(course))
    // 卡片底色：闪烁时按课程色做“深浅变化”；平时白底 + 细描边，与页面背景区分更清晰
    val cardColor = if (flash) {
        color.copy(alpha = 0.10f + 0.30f * pulse).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flash) Modifier.border(
                    2.dp,
                    color.copy(alpha = 0.35f + 0.65f * pulse),
                    RoundedCornerShape(18.dp),
                ) else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    RoundedCornerShape(18.dp),
                )
            )
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() }
                else Modifier
            ),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (highlight) {
                        TagChip(t.tagOngoing, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (course.tag.isNotBlank()) {
                        TagChip(course.tag, color)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${course.timeRange}  ·  ${course.periodLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
                val info = listOfNotNull(
                    course.teacher.ifBlank { null },
                    course.room.ifBlank { null },
                ).joinToString("  ·  ")
                if (info.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val meta = buildString {
                    if (course.code.isNotBlank()) append(course.code)
                    if (course.weeksRaw.isNotBlank()) {
                        if (isNotEmpty()) append("  ·  ")
                        append(t.weeksValue(course.weeksRaw))
                    }
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 同步状态提示（未登录/出错/进行中） */
@Composable
internal fun SyncHint(sync: SyncUi) {
    val t = LocalStrings.current
    when (sync) {
        // 【已隐藏保留】原提示含“或点「网页登录」完成统一认证”；入口隐藏后仅保留账密登录引导
        is SyncUi.NeedLogin -> StatusBanner(
            if (sync.message.isNotBlank()) t.syncHintNeedLogin + "\n" + sync.message else t.syncHintNeedLogin,
            isError = sync.message.isNotBlank(),
        )
        is SyncUi.Error -> StatusBanner(sync.message, isError = true)
        is SyncUi.Running -> StatusBanner(t.syncHintRunning)
        else -> Unit
    }
    Spacer(Modifier.height(12.dp))
}
