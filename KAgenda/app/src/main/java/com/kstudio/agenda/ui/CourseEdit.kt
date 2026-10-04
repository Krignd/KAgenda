@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.CourseEditConflict
import com.kstudio.agenda.i18n.AppStrings
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.ui.components.ChoiceChip
import com.kstudio.agenda.ui.components.InfoLine
import com.kstudio.agenda.ui.components.TagChip
import java.time.LocalDate

/** 课程块上的「已修改」记号（课表格子空间有限，用符号；详情/卡片里用完整标签） */
internal const val EDITED_MARK = "✎"

/** 课表格子标题：已修改的课程加记号 */
internal fun markedTitle(course: Course): String =
    if (course.edited) "$EDITED_MARK ${course.title}" else course.title

/** 课程一行摘要（冲突弹窗里对比「教务系统最新 / 你的修改」用） */
internal fun courseSummary(course: Course, t: AppStrings): String {
    val sb = StringBuilder()
    sb.append(t.weekdayShort(course.dayOfWeek)).append(' ').append(course.timeRange)
    if (course.room.isNotBlank()) sb.append(" · ").append(course.room)
    if (course.teacher.isNotBlank()) sb.append(" · ").append(course.teacher)
    if (course.weeksRaw.isNotBlank()) sb.append(" · ").append(t.weeksValue(course.weeksRaw))
    return sb.toString()
}

/**
 * 课程详情（底部弹层）。
 * 各入口（日视图卡片、周视图课程块、周视图时间线、日程混排）统一调用本组件，
 * 详情里提供「修改课程」入口（仅课程表/时间线里的课程需要；日程卡片不走这里）。
 */
@Composable
fun CourseDetailSheet(
    course: Course,
    onEdit: (() -> Unit)?,
    onDismiss: () -> Unit,
    onCopy: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val color = Color(CoursePalette.colorFor(course))
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                if (course.edited) {
                    TagChip(t.tagEdited, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                if (course.tag.isNotBlank()) TagChip(course.tag, color)
            }
            if (course.edited) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t.editSavedNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            InfoLine(t.detailTime, "${course.timeRange}（${course.periodLabel}）")
            InfoLine(t.detailWeekday, t.weekdayShort(course.dayOfWeek))
            if (course.teacher.isNotBlank()) InfoLine(t.detailTeacher, course.teacher)
            if (course.room.isNotBlank()) InfoLine(t.detailRoom, course.room)
            if (course.weeksRaw.isNotBlank()) InfoLine(t.detailWeeks, t.weeksValue(course.weeksRaw))
            if (course.code.isNotBlank()) InfoLine(t.detailCode, course.code)
            // 教务页面里的额外信息（开设班级 / 开课学期 / 学分 / 课程性质 / 选课备注…）
            if (course.extraInfo.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(10.dp))
                course.extraInfo.split('\n').forEach { line ->
                    if (line.isNotBlank()) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
            if (onEdit != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t.btnEditCourse) }
            }
            if (onCopy != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t.btnCopyCourse) }
            }
        }
    }
}

/**
 * 修改课程对话框：三种操作合在一个入口里（与底部三个选项对应）
 *
 * - **修改**：改课程名/老师/地点/星期/节次/周次（周次为多选），范围「仅这一次 / 全部同一课程」；
 * - **删除**：选要删掉的周次（多选；全选=整门课隐藏），范围同上；
 * - **调课**：把这一天的课整体（或只这一门）迁移到别的日期 —— 与「长按日期 → 调整至…」同一套逻辑。
 *
 * @param sourceDate 该课程块对应的日期（用于「调课」；null 时隐藏调课选项）
 * @param dayCourseCount 该日期当天的课程数（调课里「整天的课」提示用）
 * @param onSave 保存（第二参数 = 是否套用到全部同一课程）
 * @param onDelete 删除（第一参数 = 要删掉的周次；第二参数 = 是否套用到全部同一课程）
 * @param onMove 调课（第一参数 = 是否整天一起调；第二参数 = 目标日期）
 * @param onNoChange 内容与原来完全一致 / 未选择日期时提示
 */
@Composable
fun CourseEditDialog(
    initial: Course,
    sourceDate: LocalDate?,
    dayCourseCount: Int,
    onDismiss: () -> Unit,
    onSave: (Course, Boolean) -> Unit,
    onDelete: (Set<Int>, Boolean) -> Unit,
    onMove: (Boolean, LocalDate) -> Unit,
    onNoChange: () -> Unit,
) {
    val t = LocalStrings.current
    var action by remember(initial) { mutableStateOf(ACTION_MODIFY) }
    var title by remember(initial) { mutableStateOf(initial.title) }
    var teacher by remember(initial) { mutableStateOf(initial.teacher) }
    var room by remember(initial) { mutableStateOf(initial.room) }
    var weeks by remember(initial) { mutableStateOf(initial.weeksRaw) }
    var day by remember(initial) { mutableStateOf(initial.dayOfWeek) }
    var startPeriod by remember(initial) { mutableStateOf(initial.startPeriod) }
    var endPeriod by remember(initial) { mutableStateOf(initial.endPeriod) }
    var applyAll by remember(initial) { mutableStateOf(false) }
    // 删除：默认选中该课程的全部周次（= 删除整门课）
    var delWeeks by remember(initial) { mutableStateOf(initial.weeksSet()) }
    // 调课：默认「只调这一门」，选完目标日期才能确定
    var moveWholeDay by remember(initial) { mutableStateOf(false) }
    var moveTarget by remember(initial) { mutableStateOf<LocalDate?>(null) }
    var showTargetPicker by remember { mutableStateOf(false) }
    val maxWeek = remember(initial) { maxOf(20, initial.weeksSet().maxOrNull() ?: 20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (action) {
                    ACTION_DELETE -> t.editActionDelete
                    ACTION_MOVE -> t.editActionMove
                    else -> t.courseEditTitle
                }
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 操作类型（修改 / 删除 / 调课）
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChoiceChip(
                        label = t.editActionModify,
                        selected = action == ACTION_MODIFY,
                        onClick = { action = ACTION_MODIFY },
                    )
                    ChoiceChip(
                        label = t.editActionDelete,
                        selected = action == ACTION_DELETE,
                        onClick = { action = ACTION_DELETE },
                    )
                    if (sourceDate != null) {
                        ChoiceChip(
                            label = t.editActionMove,
                            selected = action == ACTION_MOVE,
                            onClick = { action = ACTION_MOVE },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (action == ACTION_MODIFY) {
                    Text(
                        text = t.courseEditIntro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(t.fieldCourseName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(t.detailTeacher) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text(t.detailRoom) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    // 星期
                    Text(
                        text = t.detailWeekday,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (d in 1..7) {
                            ChoiceChip(
                                label = t.weekdayShort(d),
                                selected = day == d,
                                onClick = { day = d },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 节次范围（下拉选择，受「课程时间」设置的节数上限约束）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PeriodPicker(
                            label = t.editPeriodStart,
                            value = startPeriod,
                            onPick = {
                                startPeriod = it
                                if (endPeriod < it) endPeriod = it
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        PeriodPicker(
                            label = t.editPeriodEnd,
                            value = endPeriod,
                            onPick = {
                                endPeriod = it
                                if (startPeriod > it) startPeriod = it
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${t.detailTime}：${PeriodTimes.rangeOf(startPeriod, endPeriod)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 周次：多选
                    WeeksPicker(
                        weeks = Course.parseWeeks(weeks).flatMapTo(LinkedHashSet()) { it.toList() },
                        onChange = { weeks = Course.encodeWeeks(it) },
                        maxWeek = maxWeek,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = t.editScopeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScopeOption(
                        label = t.editScopeOne,
                        hint = t.editScopeOneHint,
                        selected = !applyAll,
                        onSelect = { applyAll = false },
                    )
                    ScopeOption(
                        label = t.editScopeAll,
                        hint = t.editScopeAllHint,
                        selected = applyAll,
                        onSelect = { applyAll = true },
                    )
                } else if (action == ACTION_DELETE) {
                    Text(
                        text = t.editDeleteIntro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    WeeksPicker(
                        weeks = delWeeks,
                        onChange = { delWeeks = it },
                        maxWeek = maxWeek,
                        hint = t.editDeleteWeeksHint,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = t.editScopeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScopeOption(
                        label = t.editScopeOne,
                        hint = t.editScopeOneHint,
                        selected = !applyAll,
                        onSelect = { applyAll = false },
                    )
                    ScopeOption(
                        label = t.editScopeAll,
                        hint = t.editScopeAllHint,
                        selected = applyAll,
                        onSelect = { applyAll = true },
                    )
                } else {
                    Text(
                        text = t.editMoveIntro(dayCourseCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    ScopeOption(
                        label = t.editMoveWholeDay,
                        hint = t.editMoveWholeDayHint(dayCourseCount),
                        selected = moveWholeDay,
                        onSelect = { moveWholeDay = true },
                    )
                    ScopeOption(
                        label = t.editMoveSingle,
                        hint = t.editMoveSingleHint,
                        selected = !moveWholeDay,
                        onSelect = { moveWholeDay = false },
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t.editMoveTarget,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showTargetPicker = true }) {
                            Text(
                                moveTarget?.let { "%d/%d".format(it.monthValue, it.dayOfMonth) }
                                    ?: t.editMovePickDate
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (action) {
                    ACTION_DELETE -> if (delWeeks.isEmpty()) {
                        onNoChange()
                    } else {
                        onDelete(delWeeks, applyAll)
                    }
                    ACTION_MOVE -> {
                        val target = moveTarget
                        if (target == null) onNoChange() else onMove(moveWholeDay, target)
                    }
                    else -> {
                        val edited = initial.copy(
                            title = title.trim(),
                            teacher = teacher.trim(),
                            room = room.trim(),
                            weeksRaw = weeks.trim(),
                            dayOfWeek = day,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            edited = false,
                        )
                        when {
                            edited.title.isBlank() -> onNoChange()
                            edited.sameEditableContent(initial) -> onNoChange()
                            else -> onSave(edited, applyAll)
                        }
                    }
                }
            }) {
                Text(
                    when (action) {
                        ACTION_DELETE -> t.delete
                        ACTION_MOVE -> t.editActionMove
                        else -> t.save
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )

    // 调课：目标日期选择（月历弹窗）
    if (showTargetPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (moveTarget ?: sourceDate ?: LocalDate.now())
                .toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showTargetPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        moveTarget = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showTargetPicker = false
                }) { Text(t.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showTargetPicker = false }) { Text(t.cancel) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** 操作类型：修改课程信息 */
private const val ACTION_MODIFY = 0

/** 操作类型：删除 |
 */
private const val ACTION_DELETE = 1

/** 操作类型：调课（迁移到其他日期） */
private const val ACTION_MOVE = 2

/**
 * 周次多选控件：1..maxWeek 的方块（多选）+「全选 / 清空」+ 结果预览。
 *
 * @param onChange 每次点选后回传新的周次集合（空集合表示“每周”，与课表的周次语义一致）
 * @param hint 结果区文案（默认用课表的周次写法；删除场景传自己的说明）
 */
@Composable
internal fun WeeksPicker(
    weeks: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    maxWeek: Int,
    hint: String? = null,
) {
    val t = LocalStrings.current
    val raw = Course.encodeWeeks(weeks)
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = t.detailWeeks,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (w in 1..maxWeek) {
                ChoiceChip(
                    label = "$w",
                    selected = w in weeks,
                    onClick = { onChange(if (w in weeks) weeks - w else weeks + w) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((1..maxWeek).toSet()) }) { Text(t.weeksAll) }
            TextButton(onClick = { onChange(emptySet()) }) { Text(t.weeksNone) }
            Spacer(Modifier.weight(1f))
            Text(
                text = hint ?: if (raw.isBlank()) t.editWeeksHint else t.weeksValue(raw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 新增课程对话框（教务系统里没有的课，只在本地显示）。
 * 周次同样是多选；保存后进 [com.kstudio.agenda.data.CourseEditStore] 的 ADD 记录，
 * 与「修改课程」共用同一套与教务系统比对的逻辑。
 */
@Composable
fun CourseAddDialog(
    defaultDay: Int,
    defaultWeek: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onInvalid: () -> Unit,
    initial: Course? = null,
) {
    val t = LocalStrings.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var teacher by remember { mutableStateOf(initial?.teacher.orEmpty()) }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var day by remember { mutableStateOf((initial?.dayOfWeek ?: defaultDay).coerceIn(1, 7)) }
    var startPeriod by remember { mutableStateOf(initial?.startPeriod ?: 1) }
    var endPeriod by remember { mutableStateOf(initial?.endPeriod ?: 2) }
    var weeks by remember {
        mutableStateOf(initial?.weeksSet() ?: setOf(defaultWeek.coerceIn(1, 40)))
    }
    val maxWeek = remember { maxOf(20, defaultWeek, initial?.weeksSet()?.maxOrNull() ?: 20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.courseAddTitle) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = t.courseAddIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(t.fieldCourseName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text(t.detailTeacher) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text(t.detailRoom) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t.detailWeekday,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (d in 1..7) {
                        ChoiceChip(
                            label = t.weekdayShort(d),
                            selected = day == d,
                            onClick = { day = d },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PeriodPicker(
                        label = t.editPeriodStart,
                        value = startPeriod,
                        onPick = {
                            startPeriod = it
                            if (endPeriod < it) endPeriod = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    PeriodPicker(
                        label = t.editPeriodEnd,
                        value = endPeriod,
                        onPick = {
                            endPeriod = it
                            if (startPeriod > it) startPeriod = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${t.detailTime}：${PeriodTimes.rangeOf(startPeriod, endPeriod)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                WeeksPicker(weeks = weeks, onChange = { weeks = it }, maxWeek = maxWeek)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = title.trim()
                if (name.isBlank()) {
                    onInvalid()
                    return@TextButton
                }
                onSave(
                    Course(
                        title = name,
                        code = "",
                        teacher = teacher.trim(),
                        weeksRaw = Course.encodeWeeks(weeks),
                        room = room.trim(),
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        dayOfWeek = day,
                        tag = "",
                    )
                )
            }) { Text(t.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )
}

/**
 * 「调整至…」整日/单节调休对话框。
 *
 * 入口：日视图 / 周视图 / 月视图里**长按日期**（月视图为长按日期格子）。
 * 与「修改课程」共用同一套本地修正机制（不修改教务系统数据）。
 *
 * @param dayCourses 这一天**原本要上的课**（不受「节假日停课」影响，节假日才能调休）
 * @param preselected 从某门课详情进来时默认只调这一门
 */
@Composable
fun RescheduleDialog(
    sourceDate: LocalDate,
    dayCourses: List<Course>,
    preselected: Course? = null,
    onDismiss: () -> Unit,
    onConfirm: (List<Course>, LocalDate) -> Unit,
) {
    val t = LocalStrings.current
    var wholeDay by remember(sourceDate) { mutableStateOf(preselected == null) }
    var picked by remember(sourceDate) {
        mutableStateOf(if (preselected != null) setOf(preselected.id) else emptySet<String>())
    }
    var target by remember(sourceDate) { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.rescheduleTitle) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = t.rescheduleIntro(sourceDate.monthValue, sourceDate.dayOfMonth, dayCourses.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ScopeOption(
                    label = t.editMoveWholeDay,
                    hint = t.editMoveWholeDayHint(dayCourses.size),
                    selected = wholeDay,
                    onSelect = { wholeDay = true },
                )
                ScopeOption(
                    label = t.editMoveSingle,
                    hint = t.rescheduleSingleHint,
                    selected = !wholeDay,
                    onSelect = { wholeDay = false },
                )
                if (!wholeDay) {
                    for (c in dayCourses) {
                        val on = c.id in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = on,
                                    onClick = {
                                        picked = if (on) picked - c.id else picked + c.id
                                    },
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = { picked = if (on) picked - c.id else picked + c.id },
                            )
                            Text(
                                text = "${c.periodLabel} ${c.title}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.editMoveTarget,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPicker = true }) {
                        Text(
                            target?.let { "%d/%d".format(it.monthValue, it.dayOfMonth) }
                                ?: t.editMovePickDate
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tg = target ?: return@TextButton
                val courses = if (wholeDay) dayCourses else dayCourses.filter { it.id in picked }
                if (courses.isEmpty()) return@TextButton
                onConfirm(courses, tg)
            }) { Text(t.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (target ?: sourceDate).toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        target = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showPicker = false
                }) { Text(t.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(t.cancel) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** 修改范围选项（单选） */
@Composable
private fun ScopeOption(
    label: String,
    hint: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 节次下拉选择（1..PeriodTimes.count） */
@Composable
private fun PeriodPicker(
    label: String,
    value: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：第${value}节", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in 1..PeriodTimes.count) {
                DropdownMenuItem(
                    text = { Text("第${p}节") },
                    onClick = {
                        onPick(p)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * 同步后发现课程修改与教务系统都不一致时的询问弹窗。
 *
 * 每个冲突单独选择「跟随教务系统 / 保留我的修改」，也可以一次性全部处理。
 * 「稍后处理」只关闭弹窗：修改继续保留，下次同步时会再次询问。
 */
@Composable
fun CourseEditConflictDialog(
    conflicts: List<CourseEditConflict>,
    onFollow: (CourseEditConflict) -> Unit,
    onKeep: (CourseEditConflict) -> Unit,
    onFollowAll: () -> Unit,
    onKeepAll: () -> Unit,
    onLater: () -> Unit,
) {
    val t = LocalStrings.current
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(t.editConflictTitle) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = t.editConflictIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (c in conflicts) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = c.edit.edited.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (!c.edit.applyAll) TagChip(t.editScopeOne, MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (c.latest == null) {
                            Text(
                                text = t.editConflictMissing,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                text = "${t.editConflictLatest}：${courseSummary(c.latest, t)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${t.editConflictYours}：${courseSummary(c.edit.edited, t)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onFollow(c) }) { Text(t.btnFollowAcademic) }
                            TextButton(onClick = { onKeep(c) }) { Text(t.btnKeepMine) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (conflicts.size > 1) {
                    TextButton(onClick = onFollowAll) { Text(t.btnFollowAll) }
                    TextButton(onClick = onKeepAll) { Text(t.btnKeepAll) }
                }
                TextButton(onClick = onLater) { Text(t.editConflictLater) }
            }
        },
    )
}
