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
            if (onEdit != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t.btnEditCourse) }
            }
        }
    }
}

/**
 * 修改课程对话框：可改课程名/老师/地点/星期/节次/周次，并选择「仅这一次」或「全部同一课程」。
 *
 * @param onSave 保存（第二参数 = 是否套用到全部同一课程）
 * @param onNoChange 内容与原来完全一致时提示“没有检测到修改”
 */
@Composable
fun CourseEditDialog(
    initial: Course,
    onDismiss: () -> Unit,
    onSave: (Course, Boolean) -> Unit,
    onNoChange: () -> Unit,
) {
    val t = LocalStrings.current
    var title by remember(initial) { mutableStateOf(initial.title) }
    var teacher by remember(initial) { mutableStateOf(initial.teacher) }
    var room by remember(initial) { mutableStateOf(initial.room) }
    var weeks by remember(initial) { mutableStateOf(initial.weeksRaw) }
    var day by remember(initial) { mutableStateOf(initial.dayOfWeek) }
    var startPeriod by remember(initial) { mutableStateOf(initial.startPeriod) }
    var endPeriod by remember(initial) { mutableStateOf(initial.endPeriod) }
    var applyAll by remember(initial) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.courseEditTitle) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it },
                    label = { Text(t.detailWeeks) },
                    placeholder = { Text(t.editWeeksHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
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
            }) { Text(t.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )
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
