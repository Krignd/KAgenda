@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.WeekSchedule
import com.kstudio.agenda.ui.components.EmptyState
import com.kstudio.agenda.ui.components.InfoLine
import com.kstudio.agenda.ui.components.TagChip
import com.kstudio.agenda.ui.components.rememberFlashPulse
import com.kstudio.agenda.ui.components.pinchZoom
import com.kstudio.agenda.ui.components.swipeStep
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** 当前时间横线的颜色（周视图各模式共用） */
private val NOW_LINE_COLOR = Color(0xFFE53935)

/** 记录“当前分钟数”，每 30 秒刷新一次，用于当前时间横线 */
@Composable
private fun rememberNowMinutes(): Int {
    var minutes by remember { mutableStateOf(currentMinutes()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            minutes = currentMinutes()
        }
    }
    return minutes
}

private fun currentMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

@Composable
fun WeekScreen(
    vm: AppViewModel,
    timetableMode: Boolean,
    gridScroll: ScrollState = rememberScrollState(),
    contentScroll: ScrollState = rememberScrollState(),
    flash: FocusRequest? = null,
) {
    val week by vm.weekSchedule.collectAsState()
    val sync by vm.syncState.collectAsState()
    val agendaAll by vm.agenda.collectAsState()
    val selDate by vm.selectedDate.collectAsState()
    val t = LocalStrings.current
    var selected by remember { mutableStateOf<Course?>(null) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    // 双指缩放（0.7x~2x）；默认列宽：课程表模式一屏显示周一到周五，时间线模式一屏显示周一至周日
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    var zoom by rememberSaveable { mutableStateOf(1f) }

    val currentWeek = week
    if (currentWeek == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            SyncHint(sync)
            EmptyState(
                title = t.noScheduleTitle,
                hint = t.noScheduleHintWeek,
            )
        }
        return
    }

    // 日程表页只展示「日程」（「计划」页的个人计划不在此混排）
    val agenda = agendaAll.filter { !it.isPlan }

    // 闪烁反馈目标（通知/小组件定位进入）：闪烁对应日期表头，命中标题时对应课程块/日程卡一并闪烁
    val flashDate = flash?.let { LocalDate.ofEpochDay(it.epochDay) }
    val flashTitle = flash?.title.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
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
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { vm.stepWeek(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextWeek)
            }
            Spacer(Modifier.weight(1f))
            // 只显示保存图标（不显示文字）
            IconButton(onClick = { vm.saveWeekImage() }) {
                Icon(Icons.Filled.Save, contentDescription = t.saveWeekSchedule)
            }
        }

        // 纵向滚动容器：周网格 + 本周日程同屏滚动（滚动位置由页面级保持）
        Column(
            Modifier
                .fillMaxSize()
                .pinchZoom { f -> zoom = (zoom * f).coerceIn(0.7f, 2f) }
                .verticalScroll(contentScroll)
        ) {
            // 周网格区：先在内层横向滚动；已滑到边界时继续横滑才切周
            Column(
                Modifier.swipeStep(
                    key = "week-grid",
                    canStep = { dir ->
                        val max = gridScroll.maxValue
                        when {
                            max <= 0 || max == Int.MAX_VALUE -> true
                            dir > 0 -> gridScroll.value >= max
                            else -> gridScroll.value <= 0
                        }
                    },
                    onStep = { vm.stepWeek(it) },
                )
            ) {
                if (timetableMode) {
                    WeekGrid(
                        week = currentWeek,
                        hScroll = gridScroll,
                        screenW = screenW,
                        zoom = zoom,
                        flashDate = flashDate,
                        flashTitle = flashTitle,
                    ) { selected = it }
                } else {
                    WeekTimelineGrid(
                        week = currentWeek,
                        hScroll = gridScroll,
                        screenW = screenW,
                        zoom = zoom,
                        flashDate = flashDate,
                        flashTitle = flashTitle,
                    ) { selected = it }
                }
            }

            // ---------------- 我的日程（本周，按时间排序） ----------------
            // 该区域内横滑始终切换周（不在课表上，无需让横向滚动先消费）
            val monday = currentWeek.monday
            val weekEvents = agenda
                .filter { ev -> (0L..6L).any { off -> ev.coversDate(monday.plusDays(off)) } }
                .sortedWith(compareBy({ it.dateEpochDay }, { com.kstudio.agenda.model.FuzzyTime.sortKey(it.startTime) }))
            Column(
                Modifier
                    .swipeStep(key = "week-agenda", onStep = { vm.stepWeek(it) })
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.myAgendaWeek,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        editing = null
                        editorOpen = true
                    }) { Text(t.addAgendaBtn) }
                }
                if (weekEvents.isEmpty()) {
                    Text(
                        text = t.emptyAgendaHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    for (ev in weekEvents) {
                        val d = ev.date
                        AgendaCard(
                            event = ev,
                            dateLabel = "${d.monthValue}/${d.dayOfMonth} ${t.weekdayShort(d.dayOfWeek.value)}",
                            flash = flashDate != null && flashTitle.isNotBlank() &&
                                ev.title.trim() == flashTitle && ev.coversDate(flashDate),
                            onClick = {
                                editing = ev
                                editorOpen = true
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    selected?.let { course ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            CourseDetailContent(course)
        }
    }

    if (editorOpen) {
        AgendaEditorDialog(
            initial = editing,
            defaultDate = selDate,
            onDismiss = { editorOpen = false },
            onSave = { vm.saveAgendaEvent(it); editorOpen = false },
            onDelete = { id -> vm.deleteAgendaEvent(id); editorOpen = false },
        )
    }
}

// ---------------------------------------------------------------- 周网格（课程表模式）

private data class GridSegment(val course: Course?, val span: Int)
/** 把一天的课程切成“空闲 1 节 / 课程 N 节”的段落序列 */
private fun segmentsOf(courses: List<Course>): List<GridSegment> {
    val result = mutableListOf<GridSegment>()
    val sorted = courses.sortedBy { it.startPeriod }
    var period = 1
    var index = 0
    while (period <= PeriodTimes.count) {
        val course = sorted.getOrNull(index)
        if (course != null && course.startPeriod <= period) {
            val end = course.endPeriod.coerceAtLeast(period)
            result.add(GridSegment(course, end - period + 1))
            period = end + 1
            index++
        } else {
            result.add(GridSegment(null, 1))
            period++
        }
    }
    return result
}

/** 周视图表头（含节假日/周末标注）：今天高亮为主色，法定节假日为红色并显示名称，周末为三级色；[flashDate] 命中时闪烁 */
@Composable
private fun WeekHeaderRow(week: WeekSchedule, timeColWidth: Dp, dayWidth: Dp, flashDate: LocalDate? = null) {
    val today = LocalDate.now()
    val t = LocalStrings.current
    Row {
        Spacer(Modifier.width(timeColWidth))
        for (d in 1..7) {
            val date = week.dateOfWeekday(d)
            val isToday = date == today
            val holiday = HolidayTable.nameOf(date)
            val isWeekend = date.dayOfWeek.value >= 6
            val mainColor = when {
                isToday -> MaterialTheme.colorScheme.primary
                holiday != null -> MaterialTheme.colorScheme.error
                isWeekend -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val flashing = flashDate == date
            val pulse = rememberFlashPulse(flashing)
            Column(
                modifier = Modifier
                    .width(dayWidth)
                    .height(58.dp)
                    .then(
                        if (flashing) Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.42f * pulse),
                            RoundedCornerShape(10.dp),
                        ) else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = t.weekdayShort(d),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = mainColor,
                )
                Text(
                    text = "${date.monthValue}/${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) mainColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 第三行：节假日名称（无节假日时占位，保持行高一致）
                Text(
                    text = holiday ?: " ",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (holiday != null) MaterialTheme.colorScheme.error else Color.Transparent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    week: WeekSchedule,
    hScroll: ScrollState,
    screenW: Dp,
    zoom: Float,
    flashDate: LocalDate? = null,
    flashTitle: String = "",
    onSelect: (Course) -> Unit,
) {
    // 默认：课程表模式按“手机宽度刚好显示周一到周五”计算列宽；双指缩放可调整（0.7x~2x）
    val rowHeight = 58.dp * zoom
    val timeColWidth = 52.dp
    val dayWidth = ((screenW - timeColWidth) / 5f) * zoom

    // 注意：纵向滚动交给外层容器（周网格与「我的日程」同屏滚动），这里只保留横向滚动
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll)
    ) {
        // 星期表头（含节假日/周末标注）
        WeekHeaderRow(week, timeColWidth, dayWidth, flashDate = flashDate)

        Box {
            Row {
                // 节次时间列（节号 + 起止时间）
                Column(Modifier.width(timeColWidth)) {
                    for (p in 1..PeriodTimes.count) {
                        Column(
                            modifier = Modifier.height(rowHeight).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "$p",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = PeriodTimes.format(PeriodTimes.startOf(p)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = PeriodTimes.format(PeriodTimes.endOf(p)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
                // 7 天课程列（定位闪烁：命中日期+标题的课程块底色深浅变化）
                for (d in 1..7) {
                    val dayDate = week.dateOfWeekday(d)
                    DayColumn(
                        courses = week.coursesOfDay(d),
                        width = dayWidth,
                        rowHeight = rowHeight,
                        flashTitle = if (flashDate == dayDate) flashTitle else "",
                        onSelect = onSelect,
                    )
                }
            }

            // ---------------- 当前时间横线（今天在本周时显示；计时器只重组线条本身） ----------------
            TimetableNowLine(week, rowHeight, dayWidth, timeColWidth)
        }
    }
}

/** 课程表模式下：当前时间 → 行内纵坐标（位于课间时落在行边界） */
private fun timetableNowOffset(nowMin: Int, rowHeight: Dp): Dp? {
    for (p in 1..PeriodTimes.count) {
        val start = PeriodTimes.startOf(p).toSecondOfDay() / 60
        val end = PeriodTimes.endOf(p).toSecondOfDay() / 60
        if (nowMin in start..end) {
            val frac = (nowMin - start).toFloat() / (end - start).toFloat()
            return rowHeight * ((p - 1) + frac)
        }
        if (p < PeriodTimes.count) {
            val nextStart = PeriodTimes.startOf(p + 1).toSecondOfDay() / 60
            if (nowMin > end && nowMin < nextStart) return rowHeight * p
        } else if (nowMin > end) {
            return null
        }
    }
    return null
}

/** 课程表模式当前时刻横线：自持 30 秒计时器，定时刷新只重组这一小块，不带动整个网格 */
@Composable
private fun TimetableNowLine(week: WeekSchedule, rowHeight: Dp, dayWidth: Dp, timeColWidth: Dp) {
    val today = LocalDate.now()
    if (!week.containsDate(today)) return
    val nowMin = rememberNowMinutes()
    val nowY = timetableNowOffset(nowMin, rowHeight) ?: return
    Box(
        Modifier
            .offset(x = timeColWidth + dayWidth * (today.dayOfWeek.value - 1), y = nowY)
            .width(dayWidth)
            .height(2.dp)
            .background(NOW_LINE_COLOR)
    )
}

@Composable
private fun DayColumn(
    courses: List<Course>,
    width: Dp,
    rowHeight: Dp,
    flashTitle: String = "",
    onSelect: (Course) -> Unit,
) {
    // 空档与课程块的位置一次性算好：空档用 Canvas 一次绘制，课程块绝对定位，
    // 大幅减少组合节点数（切换视图与重绘都更快）
    val layout = remember(courses) {
        var row = 0
        val empties = mutableListOf<Pair<Int, Int>>()
        val blocks = mutableListOf<Triple<Course, Int, Int>>()
        for (seg in segmentsOf(courses)) {
            val course = seg.course
            if (course == null) empties.add(row to seg.span)
            else blocks.add(Triple(course, row, seg.span))
            row += seg.span
        }
        empties to blocks
    }
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Box(Modifier.width(width).height(rowHeight * PeriodTimes.count)) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 2.dp.toPx()
            val rowPx = rowHeight.toPx()
            val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            for ((row, span) in layout.first) {
                drawRoundRect(
                    color = emptyColor,
                    topLeft = Offset(inset, row * rowPx + inset),
                    size = Size(size.width - inset * 2, span * rowPx - inset * 2),
                    cornerRadius = radius,
                )
            }
        }
        for ((course, row, span) in layout.second) {
            // CoursePalette 返回 Android 原生 ARGB Int，这里转换为 Compose Color
            val color = Color(CoursePalette.colorFor(course))
            val flashing = flashTitle.isNotBlank() && course.title.trim() == flashTitle
            val pulse = rememberFlashPulse(flashing)
            Box(
                Modifier
                    .offset(y = rowHeight * row)
                    .width(width)
                    .height(rowHeight * span)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.22f + if (flashing) 0.33f * pulse else 0f))
                    .clickable { onSelect(course) }
                    .padding(6.dp)
            ) {
                Column {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.room.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = course.room,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 课程块下方显示起止时间
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = course.timeRange,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = color.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 时间线网格（非课程表模式）

private const val DAY_START_MIN = 8 * 60          // 08:00
private const val DAY_END_MIN = 22 * 60 + 15      // 22:15
private const val MINUTE_SCALE = 0.85f            // 每 1 分钟 ≈ 0.85dp，方格大小与持续时间成正比

private data class MinuteRange(val start: Int, val end: Int)

/**
 * 非课程表模式：按真实时间比例绘制。
 * - 课程块高度与持续时长成正比；
 * - 上课以外的“休息时段”（含午休/晚休）也以浅色块展示；
 * - 左侧为整点刻度，横向浅线为整点参考线；
 * - 今天在本周时绘制当前时间横线。
 */
@Composable
private fun WeekTimelineGrid(
    week: WeekSchedule,
    hScroll: ScrollState,
    screenW: Dp,
    zoom: Float,
    flashDate: LocalDate? = null,
    flashTitle: String = "",
    onSelect: (Course) -> Unit,
) {
    // 默认：时间线模式按“手机宽度刚好显示周一至周日”计算列宽；双指缩放只横向缩放（竖向保持 0.85dp/分）
    val timeColWidth = 56.dp
    val dayWidth = ((screenW - timeColWidth) / 7f) * zoom
    val totalH = ((DAY_END_MIN - DAY_START_MIN) * MINUTE_SCALE).dp

    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll)
    ) {
        // 星期表头（含节假日/周末标注）
        WeekHeaderRow(week, timeColWidth, dayWidth, flashDate = flashDate)

        Box {
            Row {
                // 整点刻度列
                Box(Modifier.width(timeColWidth).height(totalH)) {
                    for (h in 8..22) {
                        val y = ((h * 60 - DAY_START_MIN) * MINUTE_SCALE).dp
                        Text(
                            text = "%02d:00".format(h),
                            modifier = Modifier.offset(x = 4.dp, y = y - 6.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                // 7 天时间线列（定位闪烁：命中日期+标题的课程块底色深浅变化）
                for (d in 1..7) {
                    val dayDate = week.dateOfWeekday(d)
                    TimelineDayColumn(
                        courses = week.coursesOfDay(d),
                        width = dayWidth,
                        height = totalH,
                        flashTitle = if (flashDate == dayDate) flashTitle else "",
                        onSelect = onSelect,
                    )
                }
            }

            // 整点参考线（Canvas 一次绘制，节点更少）
            val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            Canvas(
                Modifier
                    .offset(x = timeColWidth)
                    .width(dayWidth * 7)
                    .height(totalH)
            ) {
                val lineH = 0.5.dp.toPx()
                for (h in 8..22) {
                    val y = ((h * 60 - DAY_START_MIN) * MINUTE_SCALE).dp.toPx()
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, lineH),
                    )
                }
            }

            // ---------------- 当前时间横线（今天在本周时显示；计时器只重组线条本身） ----------------
            TimelineNowLine(week, dayWidth, timeColWidth)
        }
    }
}

/** 时间线模式当前时刻横线：自持 30 秒计时器，定时刷新只重组这一小块 */
@Composable
private fun TimelineNowLine(week: WeekSchedule, dayWidth: Dp, timeColWidth: Dp) {
    val today = LocalDate.now()
    if (!week.containsDate(today)) return
    val nowMin = rememberNowMinutes()
    if (nowMin !in DAY_START_MIN..DAY_END_MIN) return
    val nowY = ((nowMin - DAY_START_MIN) * MINUTE_SCALE).dp
    Box(
        Modifier
            .offset(x = timeColWidth + dayWidth * (today.dayOfWeek.value - 1), y = nowY)
            .width(dayWidth)
            .height(2.dp)
            .background(NOW_LINE_COLOR)
    )
}

@Composable
private fun TimelineDayColumn(
    courses: List<Course>,
    width: Dp,
    height: Dp,
    flashTitle: String = "",
    onSelect: (Course) -> Unit,
) {
    // 休息时段 = 当日时间范围内未被课程覆盖的部分（含午休、晚休与课间）
    val rests = remember(courses) {
        val sorted = courses.sortedBy { it.startPeriod }
        val result = mutableListOf<MinuteRange>()
        var cursor = DAY_START_MIN
        for (c in sorted) {
            val s = PeriodTimes.startOf(c.startPeriod).toSecondOfDay() / 60
            val e = PeriodTimes.endOf(c.endPeriod).toSecondOfDay() / 60
            if (s > cursor) result.add(MinuteRange(cursor, minOf(s, DAY_END_MIN)))
            cursor = maxOf(cursor, e)
        }
        if (cursor < DAY_END_MIN) result.add(MinuteRange(cursor, DAY_END_MIN))
        result
    }
    val restColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)

    Box(Modifier.width(width).height(height)) {
        // 休息时段底面：Canvas 一次绘制，只有较高的时段保留起止时间文字
        Canvas(Modifier.matchParentSize()) {
            val insetX = 2.dp.toPx()
            val insetY = 1.dp.toPx()
            val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            for (r in rests) {
                drawRoundRect(
                    color = restColor,
                    topLeft = Offset(insetX, ((r.start - DAY_START_MIN) * MINUTE_SCALE).dp.toPx() + insetY),
                    size = Size(
                        size.width - insetX * 2,
                        ((r.end - r.start) * MINUTE_SCALE).dp.toPx() - insetY * 2,
                    ),
                    cornerRadius = radius,
                )
            }
        }
        for (r in rests) {
            val blockH = ((r.end - r.start) * MINUTE_SCALE).dp
            if (blockH >= 34.dp) {
                Box(
                    modifier = Modifier
                        .offset(y = ((r.start - DAY_START_MIN) * MINUTE_SCALE).dp)
                        .fillMaxWidth()
                        .height(blockH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d:%02d-%02d:%02d".format(
                            r.start / 60, r.start % 60, r.end / 60, r.end % 60
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        // 课程块（高度与起止时间成正比）
        for (course in courses) {
            val s = PeriodTimes.startOf(course.startPeriod).toSecondOfDay() / 60
            val e = PeriodTimes.endOf(course.endPeriod).toSecondOfDay() / 60
            val mins = e - s
            val blockH = (mins * MINUTE_SCALE).dp
            val color = Color(CoursePalette.colorFor(course))
            val flashing = flashTitle.isNotBlank() && course.title.trim() == flashTitle
            val pulse = rememberFlashPulse(flashing)
            Box(
                Modifier
                    .offset(y = ((s - DAY_START_MIN) * MINUTE_SCALE).dp)
                    .fillMaxWidth()
                    .height(blockH)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.22f + if (flashing) 0.33f * pulse else 0f))
                    .clickable { onSelect(course) }
                    .padding(5.dp)
            ) {
                Column {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (mins < 50) 1 else if (mins < 100) 3 else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (blockH >= 46.dp && course.room.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = course.room,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (blockH >= 62.dp) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = course.timeRange,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = color.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 详情

@Composable
private fun CourseDetailContent(course: Course) {
    val t = LocalStrings.current
    // CoursePalette 返回 Android 原生 ARGB Int，这里转换为 Compose Color
    val color = Color(CoursePalette.colorFor(course))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (course.tag.isNotBlank()) TagChip(course.tag, color)
        }
        Spacer(Modifier.height(16.dp))
        InfoLine(t.detailTime, "${course.timeRange}（${course.periodLabel}）")
        InfoLine(t.detailWeekday, t.weekdayShort(course.dayOfWeek))
        if (course.teacher.isNotBlank()) InfoLine(t.detailTeacher, course.teacher)
        if (course.room.isNotBlank()) InfoLine(t.detailRoom, course.room)
        if (course.weeksRaw.isNotBlank()) InfoLine(t.detailWeeks, t.weeksValue(course.weeksRaw))
        if (course.code.isNotBlank()) InfoLine(t.detailCode, course.code)
    }
}
