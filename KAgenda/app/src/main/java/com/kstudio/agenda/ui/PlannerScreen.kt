package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kstudio.agenda.data.*
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.SemesterSchedule
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun plannerText(zh: String, en: String, fr: String) = when (AppText.resolve(AppText.state.value).tag) {
    "zh" -> zh
    "fr" -> fr
    else -> en
}

internal val LocalPlanner = staticCompositionLocalOf<AppViewModel> { error("Planner requires AppViewModel") }
internal val LocalTeachingAnchor = staticCompositionLocalOf<LocalDate?> { null }
internal val LocalPlannerSchool = staticCompositionLocalOf { "" }
internal val LocalPlannerSemester = staticCompositionLocalOf<SemesterSchedule?> { null }
internal val LocalPlannerTags = compositionLocalOf<Map<String, List<String>>> { emptyMap() }
internal val LocalCourseTagDefinitions = compositionLocalOf { PlannerData().courseTagDefinitions }
internal val LocalOpenLessonNote = staticCompositionLocalOf<(LessonRef, String?) -> Unit> { { _, _ -> } }

internal fun lessonTagLabel(tag: CourseTagDefinition) = tag.label ?: when (tag.id) {
    "exam" -> plannerText("考试", "Exam", "Examen")
    "lab" -> plannerText("实验", "Lab", "TP")
    "quiz" -> plannerText("测验", "Quiz", "Test")
    else -> tag.id
}

@Composable
internal fun lessonTags(course: Course, date: LocalDate): List<String> {
    val tags = LocalPlannerTags.current
    if (tags.isEmpty()) return emptyList()
    val ref = LessonRef.of(LocalPlannerSchool.current, course, date, LocalPlannerSemester.current)
    val definitions = LocalCourseTagDefinitions.current.associateBy { it.id }
    return tags[ref.id].orEmpty().mapNotNull { definitions[it]?.let(::lessonTagLabel) }
}

@Composable
internal fun taggedCourseTitle(course: Course, date: LocalDate): String =
    (listOf(markedTitle(course)) + lessonTags(course, date)).joinToString(" · ")

@Composable
internal fun BottomBarSettings(vm: AppViewModel, tabs: List<Pair<String, String>>) {
    val data by PlannerStore.data.collectAsState()
    val labels = tabs.toMap()
    val ordered = data.tabs + tabs.map { it.first }.filterNot { it in data.tabs }
    var dragging by remember(data.tabs) { mutableStateOf<String?>(null) }
    var dragOffset by remember(data.tabs) { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    Column {
        ordered.forEach { tab ->
            key(tab) {
                val index = data.tabs.indexOf(tab)
                val movable = index >= 0 && data.tabs.size > 1
                val active = dragging == tab
                val density = LocalDensity.current
                fun distanceTo(destination: Int): Float {
                    val from = minOf(index, destination)
                    val to = maxOf(index, destination)
                    val fallback = with(density) { 48.dp.roundToPx() }
                    val pixels = (from until to).sumOf { rowHeights[data.tabs[it]] ?: fallback }.toFloat()
                    return if (destination < index) -pixels else pixels
                }
                Row(
                    Modifier.fillMaxWidth()
                        .zIndex(if (active) 1f else 0f)
                        .pointerInput(tab, data.tabs) {
                            if (!movable) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = tab; dragOffset = 0f },
                                onDragCancel = { dragging = null; dragOffset = 0f },
                                onDragEnd = {
                                    val target = data.tabs.indices.minByOrNull { abs(distanceTo(it) - dragOffset) } ?: index
                                    dragging = null
                                    dragOffset = 0f
                                    if (target != index) vm.movePlannerTab(tab, target - index)
                                },
                            ) { change, delta ->
                                change.consume()
                                dragOffset = (dragOffset + delta.y).coerceIn(distanceTo(0), distanceTo(data.tabs.lastIndex))
                            }
                        }
                        .offset { IntOffset(0, if (active) dragOffset.roundToInt() else 0) }
                        .onSizeChanged { rowHeights[tab] = it.height }
                        .then(if (active) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = index >= 0, enabled = tab != "Settings", onCheckedChange = { checked ->
                        vm.editPlanner { it.copy(tabs = if (checked) (it.tabs + tab).distinct() else it.tabs - tab) }
                    })
                    Text(labels[tab].orEmpty(), Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { vm.movePlannerTab(tab, -1) }) {
                        Icon(Icons.Default.KeyboardArrowUp, plannerText("上移", "Move up", "Monter"))
                    }
                    IconButton(enabled = index >= 0 && index < data.tabs.lastIndex, onClick = { vm.movePlannerTab(tab, 1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, plannerText("下移", "Move down", "Descendre"))
                    }
                }
            }
        }
        TextButton(onClick = { vm.editPlanner { it.copy(tabs = PlannerData().tabs) } }) {
            Text(plannerText("恢复默认底栏", "Reset bottom bar", "Rétablir la barre"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetTapActions(vm: AppViewModel) {
    val data by PlannerStore.data.collectAsState()
    val t = LocalStrings.current
    val actions = linkedMapOf(
        "" to plannerText("默认", "Default", "Par défaut"),
        "course" to plannerText("定位当前课程", "Find current class", "Afficher le cours actuel"),
        "camera" to plannerText("拍照记作业", "Capture homework", "Photographier un devoir"),
        "countdown" to plannerText("倒计时", "Countdown", "Compte à rebours"),
        "agenda" to t.tabSchedule,
    )
    Column {
        listOf(
            "next" to plannerText("下一节课", "Next class", "Prochain cours"),
            "overview" to plannerText("日程和课表概览", "Agenda and timetable", "Agenda et emploi du temps"),
            "countdown" to plannerText("倒计时", "Countdown", "Compte à rebours"),
        ).forEach { (key, label) ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text(actions[data.widgetActions[key]] ?: actions.getValue(""))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f)) {
                        actions.forEach { (action, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                menu = false
                                vm.editPlanner { it.copy(widgetActions = it.widgetActions + (key to action)) }
                            })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannerDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    yearRange: IntRange = minOf(1900, initial.year)..maxOf(2100, initial.year),
    onSelected: (LocalDate) -> Unit,
) {
    val t = LocalStrings.current
    val anchor = LocalTeachingAnchor.current
    // Material keeps the day grid mounted beneath the year list. Both layers must be opaque in glass themes.
    val colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f))
    val calendar = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L, yearRange = yearRange)
    var byWeek by rememberSaveable { mutableStateOf(false) }
    var week by rememberSaveable {
        mutableStateOf(anchor?.let { (Math.floorDiv(initial.toEpochDay() - it.toEpochDay(), 7L) + 1).coerceIn(1, 40).toString() } ?: "1")
    }
    var weekday by rememberSaveable { mutableIntStateOf(initial.dayOfWeek.value) }
    val weekNumber = week.toIntOrNull()?.takeIf { it in 1..Course.MAX_WEEK }
    val useWeek = byWeek && anchor != null
    DatePickerDialog(onDismissRequest = onDismiss, colors = colors, confirmButton = {
        TextButton(enabled = if (useWeek) weekNumber != null else calendar.selectedDateMillis != null, onClick = {
            val selected = if (useWeek && weekNumber != null) {
                anchor.plusWeeks((weekNumber - 1).toLong()).plusDays((weekday - 1).toLong())
            } else calendar.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)) }
            if (selected != null) onSelected(selected)
        }) { Text(t.confirm) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }) {
        Column(Modifier.fillMaxWidth()) {
            if (anchor != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                TextButton(onClick = { byWeek = false }, enabled = useWeek) { Text(plannerText("日期", "Calendar", "Calendrier")) }
                TextButton(onClick = { byWeek = true }, enabled = !useWeek) { Text(plannerText("教学周", "Teaching week", "Semaine de cours")) }
            }
            if (!useWeek) {
                DatePicker(state = calendar, colors = colors)
            } else {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(week, { week = it.filter(Char::isDigit).take(2) }, singleLine = true,
                        label = { Text(plannerText("第几周（1–40）", "Week (1–40)", "Semaine (1–40)")) })
                    (1..7).forEach { day ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).selectable(day == weekday, role = Role.RadioButton) { weekday = day },
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(day == weekday, onClick = null, modifier = Modifier.padding(horizontal = 10.dp))
                            Text(t.weekdayShort(day), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (weekNumber != null) {
                        Text(t.weeksValue(weekNumber.toString()) + " " + t.weekdayShort(weekday) + " · " +
                            anchor.plusWeeks((weekNumber - 1).toLong()).plusDays((weekday - 1).toLong()),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
