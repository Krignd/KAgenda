package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.kstudio.agenda.ui.components.cardBaseColor
import com.kstudio.agenda.ui.components.cardShadowElevation
import com.kstudio.agenda.ui.components.cardTonalElevation
import com.kstudio.agenda.ui.components.rememberFlashPulse
import com.kstudio.agenda.ui.components.swipeStep
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

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
    // 读一下设置：课程时间/节数变化后 PeriodTimes 是全局对象，Compose 无法感知，
    // 这里用设置值作为 remember 的 key 来触发重新组合
    val settings by vm.settings.collectAsState()
    val t = LocalStrings.current

    // 日程新建/编辑对话框状态
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgendaEvent?>(null) }
    // 课程详情（点课程卡片）与课程修改（详情 → 「修改课程」）
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    // 新增课程（本地添加）与「长按日期 → 调整至…」
    var addCourseFor by remember { mutableStateOf<LocalDate?>(null) }
    var copyFrom by remember { mutableStateOf<Course?>(null) }
    var rescheduleSource by remember { mutableStateOf<LocalDate?>(null) }

    val currentWeek = week
    // 没有课表数据（未登录 / 同步失败 / 已清缓存）时不再整页早退：
    // 依然显示日期条、周导航与本地日程/计划，只是课程区为空
    val monday = currentWeek?.monday ?: mondayOfDate(selected)

    // 日程表页只展示「日程」（「计划」页的个人计划不在此混排）
    val agenda = agendaAll.filter { !it.isPlan }

    // 闪烁反馈目标（通知/小组件定位进入）：日期始终闪烁，标题命中时对应卡片一并闪烁
    val flashDate = flash?.let { LocalDate.ofEpochDay(it.epochDay) }
    val flashTitle = flash?.title.orEmpty()
    val matchesFlash: (String) -> Boolean = { title ->
        flashDate == selected && flashTitle.isNotBlank() && title.trim() == flashTitle
    }

    // 外层不加滑动手势：在周导航按钮上滑动不应切换日期；
    // 日期条自身支持左右连贯滑动切周（见 DayStripPager），内容区手势只作用于下方列表
    Column(Modifier.fillMaxSize()) {
        DayStripPager(
            vm = vm,
            monday = monday,
            selected = selected,
            flashDate = flashDate,
            onLongPressDate = { d ->
                if (vm.coursesRawOnDate(d).isEmpty()) vm.message(t.noCoursesToday) else rescheduleSource = d
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.stepWeek(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = t.prevWeek)
            }
            Text(
                text = buildString {
                    // 无课表数据时用已保存的学期锚点推算周次（仍让用户知道现在是第几周）
                    val weekNo = currentWeek?.weekNo ?: vm.teachingWeekOf(selected)
                    if (weekNo != null) append(t.weekNo(weekNo))
                    val range = currentWeek?.weekRangeLabel?.takeIf { it.isNotBlank() }
                        ?: weekRangeLabelOf(monday)
                    if (range.isNotBlank()) append("（${range}）")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { vm.stepWeek(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = t.nextWeek)
            }
            // 回到今天：从任意周快速跳回当前日期（周次标签同步更新）
            if (selected != LocalDate.now()) {
                TextButton(onClick = { vm.goToday() }) { Text(t.backToToday) }
            }
            Spacer(Modifier.weight(1f))
            // 新增课程（仅本地显示；长按日期可把当天的课「调整至…」）
            // 两个图标统一显式尺寸，避免新增按钮后保存图标看起来变小
            IconButton(
                onClick = { addCourseFor = selected },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = t.btnAddCourse)
            }
            IconButton(
                onClick = { vm.saveDayImage(selected) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Filled.Save, contentDescription = t.saveDaySchedule)
            }
        }

        val courses = remember(selected, currentWeek, settings.periodTimesRaw) {
            currentWeek?.coursesOnDate(selected).orEmpty()
        }
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
                // 无课表数据：把同步引导放在列表首项，不影响下方日程展示
                if (currentWeek == null) {
                    item(key = "sync-hint") { SyncHint(sync) }
                }
                if (courses.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = if (currentWeek == null) t.noScheduleTitle else t.noCoursesToday,
                            hint = if (currentWeek == null) t.noScheduleHintDay else t.noCoursesTodayHint,
                        )
                    }
                } else {
                    items(courses, key = { it.id }) { course ->
                        CourseCard(
                            course = course,
                            highlight = isOngoing(course, selected),
                            flash = matchesFlash(course.title),
                            onClick = { detailCourse = course },
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
                // 无课表数据：把同步引导放在列表首项，不影响下方日程展示
                if (currentWeek == null) {
                    item(key = "sync-hint") { SyncHint(sync) }
                }
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
                        EmptyState(
                            title = if (currentWeek == null) t.noScheduleTitle else t.noCoursesToday,
                            hint = if (currentWeek == null) t.noScheduleHintDay else t.noCoursesTodayHint,
                        )
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
                                onClick = { detailCourse = item.course },
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

    // 课程详情 → 修改课程（与周视图同一套弹层，交互保持一致）
    detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
            onEdit = {
                editingCourse = course
                detailCourse = null
            },
            onDismiss = { detailCourse = null },
            onCopy = {
                copyFrom = course
                detailCourse = null
            },
        )
    }

    editingCourse?.let { course ->
        CourseEditDialog(
            initial = course,
            sourceDate = selected,
            dayCourseCount = vm.coursesRawOnDate(selected).size,
            onDismiss = { editingCourse = null },
            onSave = { edited, applyAll ->
                vm.saveCourseEdit(course, edited, applyAll)
                editingCourse = null
            },
            onDelete = { weeks, applyAll ->
                vm.deleteCourse(course, weeks, applyAll)
                editingCourse = null
            },
            onMove = { wholeDay, target ->
                val day = vm.coursesRawOnDate(selected)
                vm.moveCourses(selected, if (wholeDay) day else listOf(course), target)
                editingCourse = null
            },
            onNoChange = {
                vm.message(t.editNoChange)
                editingCourse = null
            },
        )
    }

    // 新增 / 复制课程（复制 = 用原课程信息预填新增页面）
    if (addCourseFor != null || copyFrom != null) {
        val base = copyFrom
        val date = addCourseFor ?: selected
        CourseAddDialog(
            defaultDay = base?.dayOfWeek ?: date.dayOfWeek.value,
            defaultWeek = vm.teachingWeekOf(date) ?: 1,
            initial = base,
            onDismiss = {
                addCourseFor = null
                copyFrom = null
            },
            onSave = { c ->
                vm.addCourse(c)
                addCourseFor = null
                copyFrom = null
            },
            onInvalid = { vm.message(t.editNoChange) },
        )
    }

    // 长按日期 → 调整至…（整天/单节调休）
    rescheduleSource?.let { src ->
        val dayCourses = remember(src, currentWeek) { vm.coursesRawOnDate(src) }
        RescheduleDialog(
            sourceDate = src,
            dayCourses = dayCourses,
            onDismiss = { rescheduleSource = null },
            onConfirm = { courses, target ->
                vm.moveCourses(src, courses, target)
                rescheduleSource = null
            },
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

/** 日期所在周的周一（ISO 周：周一为起点） */
private fun mondayOfDate(date: LocalDate): LocalDate = date.with(java.time.DayOfWeek.MONDAY)

/** "09/28~10/04" 形式的周范围（无课表数据时用于周次标签） */
private fun weekRangeLabelOf(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return "%02d/%02d~%02d/%02d".format(
        monday.monthValue, monday.dayOfMonth, sunday.monthValue, sunday.dayOfMonth
    )
}

/** 周 → 日期条页索引 */
private fun pageIndexOf(minMonday: LocalDate, monday: LocalDate, totalPages: Int): Int =
    ChronoUnit.WEEKS.between(minMonday, monday).toInt().coerceIn(0, totalPages - 1)

/**
 * 日视图顶部日期条（可连贯滑动）：
 * - 每页 = 一周，页面之间左右滑动即可切周，不再固定于当前这一周；
 * - 滑到相邻周后保持同一星期几（周一→周一…），与内容区按天滑动的手感一致；
 * - 外部改变周次（上一周/下一周按钮、内容区滑动跨周、通知/小组件定位）时日期条自动滑到对应页。
 *
 * 注意：这里用 [rememberUpdatedState] 保存「当前周 / 选中日期」的最新值。
 * 之前直接捕获闭包变量导致：只要日期条发生一次动画滚动，就会用**首次组合时**的旧日期
 * 重算选中日，从而出现“选周四切到下一周变回周一”“逐日滑到上一周从周日跳回周一/周六”等异常。
 * 另外用 [programmaticTarget] 标记程序化滚动目标页，避免动画过程被误判为用户滑动。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DayStripPager(
    vm: AppViewModel,
    monday: LocalDate,
    selected: LocalDate,
    flashDate: LocalDate? = null,
    onLongPressDate: ((LocalDate) -> Unit)? = null,
) {
    // 页范围覆盖应用允许的导航区间（与 vm.navMinDate/navMaxDate 一致）
    val minMonday = remember { mondayOfDate(vm.navMinDate) }
    val totalPages = remember(minMonday) {
        (ChronoUnit.WEEKS.between(minMonday, mondayOfDate(vm.navMaxDate)) + 1).toInt()
            .coerceAtLeast(1)
    }
    val pagerState = rememberPagerState(
        initialPage = pageIndexOf(minMonday, monday, totalPages),
    ) { totalPages }

    val latestMonday by rememberUpdatedState(monday)
    val latestSelected by rememberUpdatedState(selected)
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }

    // 外部换周 / 选日期 → 日期条滚到对应页（同一周内选日期不滚动）
    LaunchedEffect(monday, totalPages) {
        val target = pageIndexOf(minMonday, monday, totalPages)
        if (pagerState.currentPage == target && !pagerState.isScrollInProgress) return@LaunchedEffect
        programmaticTarget = target
        try {
            pagerState.animateScrollToPage(target)
        } finally {
            programmaticTarget = null
        }
    }
    // 用户滑动日期条 → 换周（保持同一星期几）；程序化滚动与中间态一律忽略
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (scrolling) return@collect
                if (page == programmaticTarget) return@collect
                val pageMonday = minMonday.plusWeeks(page.toLong())
                if (pageMonday != mondayOfDate(latestSelected)) {
                    // 换周时保持同一星期几
                    vm.selectDate(pageMonday.plusDays((latestSelected.dayOfWeek.value - 1).toLong()))
                }
            }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
        DayStrip(
            monday = minMonday.plusWeeks(page.toLong()),
            selected = selected,
            onSelect = vm::selectDate,
            flashDate = flashDate,
            onLongPress = onLongPressDate,
        )
    }
}

/** 一周 7 个日期框（等宽），供日视图与「计划」页共用；[flashDate] 命中时边框闪烁 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DayStrip(
    monday: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    flashDate: LocalDate? = null,
    onLongPress: ((LocalDate) -> Unit)? = null,
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
                // 周末底色：页面底 ≈ #F2F5F9、工作日卡片 = 纯白，而本主题的 surfaceVariant 与页面底几乎同色
                // （实测周六卡 240,243,247 vs 页面底 242,245,249，只差 2/255）→ 旧写法“周末卡片”看着与背景融为一体。
                // 这里改用 onSurface 的 12% 淡染：浅色下 ≈ #E4E6EA（明显深于页面底、又明显区别于白卡），
                // 深色与玻璃主题下同样能拉开层次。
                isWeekend -> {
                    val weekendColor = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.12f)
                        .compositeOver(cardBaseColor())
                    Brush.linearGradient(listOf(weekendColor, weekendColor))
                }
                else -> Brush.linearGradient(
                    listOf(cardBaseColor(), cardBaseColor())
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
                    .combinedClickable(
                        onClick = { onSelect(date) },
                        onLongClick = onLongPress?.let { lp -> { lp(date) } },
                    )
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
    // 卡片底色：白天纯白（比页面底色更白、不带主色染色），夜间用 surface；
    // 闪烁时按课程色做“深浅变化”
    val base = cardBaseColor()
    val cardColor = if (flash) {
        color.copy(alpha = 0.10f + 0.30f * pulse).compositeOver(base)
    } else {
        base
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        tonalElevation = cardTonalElevation(),
        shadowElevation = cardShadowElevation(),
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
                    if (course.edited) {
                        TagChip(t.tagEdited, MaterialTheme.colorScheme.primary)
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
