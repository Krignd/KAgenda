@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.AgendaTextParser
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.AgendaTypes
import com.kstudio.agenda.ui.components.RepeatRulePicker
import com.kstudio.agenda.ui.components.TagChip
import com.kstudio.agenda.ui.components.rememberFlashPulse
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** 日程卡片（日/周/进行中/计划页共用；点击进入编辑）；[flash] 为定位闪烁反馈 */
@Composable
fun AgendaCard(
    event: AgendaEvent,
    onClick: () -> Unit,
    dateLabel: String? = null,
    extraLine: String? = null,
    flash: Boolean = false,
) {
    val t = LocalStrings.current
    val pulse = rememberFlashPulse(flash)
    val accent = if (event.displayColor != 0) {
        Color(event.displayColor)
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    // 卡片底色：闪烁时按条目色做“深浅变化”提醒；平时白底 + 细描边，与页面背景对比更清晰
    val cardColor = if (flash) {
        accent.copy(alpha = 0.10f + 0.30f * pulse).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val ongoing = event.isOngoing()
    // 类型标签：优先本地化类型名；否则按 计划/长日程/日程 显示
    val tagText = when {
        event.type.isNotBlank() -> t.typeLabel(event.type).ifBlank { event.type }
        event.isPlan -> t.tagPlan
        event.isLong -> t.tagLong
        else -> t.tagAgenda
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
                    accent.copy(alpha = 0.35f + 0.65f * pulse),
                    RoundedCornerShape(18.dp),
                ) else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    RoundedCornerShape(18.dp),
                )
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (ongoing) {
                        TagChip(t.tagOngoing, accent)
                        Spacer(Modifier.width(6.dp))
                    }
                    TagChip(text = tagText, color = accent)
                }
                val detail = listOfNotNull(
                    dateLabel,
                    event.rangeLabel.ifBlank { null },
                    t.repeatLabel(event.repeatRule).ifBlank { null },
                ).joinToString("  ·  ")
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
                if (!extraLine.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = extraLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val info = listOfNotNull(
                    event.location.ifBlank { null },
                    event.note.ifBlank { null },
                ).joinToString("  ·  ")
                if (info.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/**
 * 日程 / 计划 新建与编辑对话框。
 * - 顶部「粘贴通知文字」区：粘贴后一键智能识别 标题/类型/日期/时间/地点/备注；
 * - 日期：日历弹窗（月历/年历）选择；时间：时/分滚轮弹窗（类似手机闹钟）选择；
 * - 支持类型（默认颜色）、自定义颜色、短日程/长日程切换。
 * @param initial null=新建（默认日期取 defaultDate），非空=编辑
 * @param asPlan 新建时的归属：true=「计划」页，false=「日程表」
 */
@Composable
fun AgendaEditorDialog(
    initial: AgendaEvent?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (AgendaEvent) -> Unit,
    onDelete: (String) -> Unit,
    asPlan: Boolean = false,
) {
    val t = LocalStrings.current
    // 计划语境：用于“短/长计划”“重复规则”等仅计划适用的文案与选项
    val isPlanContext = initial?.isPlan ?: asPlan
    // 编辑字段用 rememberSaveable：旋转屏幕 / 系统重建后输入不丢失
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: "") }
    var colorArgb by rememberSaveable { mutableStateOf(initial?.colorArgb ?: 0) }
    var isLong by rememberSaveable { mutableStateOf(initial?.isLong ?: false) }
    var repeatRule by rememberSaveable { mutableStateOf(initial?.repeatRule ?: "") }
    // 日期以 epochDay 保存（可保存类型）；startDate/endDate 为组合期派生值
    var startDateEpoch by rememberSaveable { mutableStateOf((initial?.date ?: defaultDate).toEpochDay()) }
    var endDateEpoch by rememberSaveable {
        mutableStateOf((initial?.endDate ?: (initial?.date ?: defaultDate)).toEpochDay())
    }
    val startDate = LocalDate.ofEpochDay(startDateEpoch)
    val endDate = LocalDate.ofEpochDay(endDateEpoch)
    var startTime by rememberSaveable { mutableStateOf(initial?.startTime ?: "") }
    var endTime by rememberSaveable { mutableStateOf(initial?.endTime ?: "") }
    var location by rememberSaveable { mutableStateOf(initial?.location ?: "") }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var pasteText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }
    var parseMsg by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val aiContext = LocalContext.current
    // 当前打开的弹窗选择器：""=无；"startDate"/"endDate"；"startTime"/"endTime"
    var picker by remember { mutableStateOf("") }

    // 关闭前检查是否有未保存的修改（任何字段被改动就提示确认，防止点遮罩/返回键误丢内容）
    val initialSnapshot = remember {
        listOf(
            initial?.title ?: "", initial?.type ?: "", (initial?.colorArgb ?: 0).toString(),
            (initial?.isLong ?: false).toString(), initial?.repeatRule ?: "",
            (initial?.date ?: defaultDate).toEpochDay().toString(),
            (initial?.endDate ?: (initial?.date ?: defaultDate)).toEpochDay().toString(),
            initial?.startTime ?: "", initial?.endTime ?: "",
            initial?.location ?: "", initial?.note ?: "",
        )
    }
    fun dirtyNow(): Boolean = listOf(
        title, type, colorArgb.toString(), isLong.toString(), repeatRule,
        startDateEpoch.toString(), endDateEpoch.toString(),
        startTime, endTime, location, note,
    ) != initialSnapshot

    AlertDialog(
        onDismissRequest = { if (dirtyNow()) confirmDiscard = true else onDismiss() },
        title = {
            Text(
                if (initial == null) {
                    if (asPlan) t.editorAddPlan else t.editorAddAgenda
                } else {
                    if (initial.isPlan) t.editorEditPlan else t.editorEditAgenda
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // ---------- 粘贴文字：智能识别时间/地点/类型 ----------
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = t.pasteLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            placeholder = {
                                Text(
                                    text = t.pastePlaceholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (pasteText.isBlank()) return@Button
                                    val parsed = AgendaTextParser.parse(pasteText, startDate)
                                    if (parsed.title.isNotBlank()) title = parsed.title
                                    // 仅在识别出明确类型时才覆盖用户已选类型
                                    if (parsed.type.isNotBlank()) type = parsed.type
                                    startDateEpoch = parsed.dateEpochDay
                                    if (isLong && endDateEpoch < startDateEpoch) endDateEpoch = startDateEpoch
                                    startTime = parsed.startTime
                                    endTime = parsed.endTime
                                    if (parsed.location.isNotBlank()) location = parsed.location
                                    if (note.isBlank()) note = parsed.note
                                    parseMsg = when {
                                        parsed.hasDate && parsed.hasTime -> t.parseOk
                                        parsed.hasDate || parsed.hasTime || parsed.location.isNotBlank() -> t.parsePartial
                                        else -> t.parseFail
                                    }
                                },
                                enabled = pasteText.isNotBlank(),
                            ) { Text(t.pasteBtn) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (pasteText.isBlank() || aiBusy) return@OutlinedButton
                                    aiBusy = true
                                    parseMsg = t.aiRunning
                                    scope.launch {
                                        try {
                                            val key = SettingsStore.effectiveAiKey(aiContext)
                                            if (key.isNullOrBlank()) {
                                                parseMsg = t.aiNeedKey
                                                return@launch
                                            }
                                            val model = SettingsStore.effectiveAiModel(aiContext)
                                            val reply = AiClient.chat(
                                                apiKey = key,
                                                model = model,
                                                systemPrompt = AiSkills.parseEventSystemPrompt,
                                                userPrompt = AiSkills.userPrompt(pasteText, startDate),
                                            )
                                            val ev = AiSkills.parseReply(reply)
                                            if (ev == null) {
                                                parseMsg = t.parseFail
                                            } else {
                                                if (ev.title.isNotBlank()) title = ev.title
                                                if (ev.type.isNotBlank() && ev.type in AgendaTypes.ORDER) type = ev.type
                                                ev.date?.let {
                                                    startDateEpoch = it.toEpochDay()
                                                    if (isLong && endDateEpoch < startDateEpoch) endDateEpoch = startDateEpoch
                                                }
                                                if (ev.startTime.isNotBlank()) startTime = ev.startTime
                                                if (ev.endTime.isNotBlank()) endTime = ev.endTime
                                                if (ev.location.isNotBlank()) location = ev.location
                                                if (ev.note.isNotBlank() && note.isBlank()) note = ev.note
                                                parseMsg = t.aiOk
                                            }
                                        } catch (e: Throwable) {
                                            parseMsg = (e.message ?: t.parseFail).take(60)
                                        } finally {
                                            aiBusy = false
                                        }
                                    }
                                },
                                enabled = pasteText.isNotBlank() && !aiBusy,
                            ) { Text(t.aiBtn) }
                            if (parseMsg.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = parseMsg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (parseMsg == t.parseFail) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(t.fieldTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---------- 类型（默认颜色，按当前语言显示） ----------
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AgendaTypes.ORDER.forEach { key ->
                        FilterChip(
                            selected = type == key,
                            onClick = { type = if (type == key) "" else key },
                            label = { Text(t.typeLabel(key)) },
                        )
                    }
                }

                // ---------- 颜色（自动=类型默认色；也可自选） ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.labelColor,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = colorArgb == 0,
                        onClick = { colorArgb = 0 },
                        label = { Text(t.colorAuto) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AgendaTypes.PALETTE.forEach { c ->
                            ColorDot(
                                color = Color(c),
                                selected = colorArgb == c,
                                onClick = { colorArgb = c },
                            )
                        }
                    }
                }

                // ---------- 短 / 长（计划语境显示“短计划 / 长计划”） ----------
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isLong,
                        onClick = { isLong = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text(if (isPlanContext) t.segShortPlan else t.segShort) },
                    )
                    SegmentedButton(
                        selected = isLong,
                        onClick = { isLong = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text(if (isPlanContext) t.segLongPlan else t.segLong) },
                    )
                }

                if (!isLong) {
                    PickerField(
                        label = t.fieldDate,
                        value = startDate.toString(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { picker = "startDate" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = t.fieldStartTime,
                            value = startTime.ifBlank { t.unsetTime },
                            modifier = Modifier.weight(1f),
                            onClick = { picker = "startTime" },
                            onClear = if (startTime.isNotBlank()) ({ startTime = "" }) else null,
                        )
                        PickerField(
                            label = t.fieldEndTime,
                            value = endTime.ifBlank { t.unsetTime },
                            modifier = Modifier.weight(1f),
                            onClick = { picker = "endTime" },
                            onClear = if (endTime.isNotBlank()) ({ endTime = "" }) else null,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = t.fieldStartDate,
                            value = startDate.toString(),
                            modifier = Modifier.weight(1.3f),
                            onClick = { picker = "startDate" },
                        )
                        PickerField(
                            label = t.fieldStartTime,
                            value = startTime.ifBlank { "00:00" },
                            modifier = Modifier.weight(1f),
                            onClick = { picker = "startTime" },
                            onClear = if (startTime.isNotBlank()) ({ startTime = "" }) else null,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField(
                            label = t.fieldEndDate,
                            value = endDate.toString(),
                            modifier = Modifier.weight(1.3f),
                            onClick = { picker = "endDate" },
                        )
                        PickerField(
                            label = t.fieldEndTime,
                            value = endTime.ifBlank { "23:59" },
                            modifier = Modifier.weight(1f),
                            onClick = { picker = "endTime" },
                            onClear = if (endTime.isNotBlank()) ({ endTime = "" }) else null,
                        )
                    }
                }

                if (!isLong) {
                    // 重复规则：日程与计划一致（每周/隔周/按天/每月）
                    RepeatRulePicker(
                        rule = repeatRule,
                        onChange = { repeatRule = it },
                        modifier = Modifier.fillMaxWidth(),
                        anchorDayOfWeek = startDate.dayOfWeek.value,
                    )
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(t.fieldLocation) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(t.fieldNote) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val titleText = title.trim()
                if (titleText.isBlank()) {
                    error = t.errTitle
                    return@TextButton
                }
                // 选了「每周/隔周」却没选任何星期：不能保存（否则规则无效、条目不会出现）
                if (!isLong && isWeekdayRuleEmpty(repeatRule)) {
                    error = t.errRepeatNoDay
                    return@TextButton
                }
                if (isLong) {
                    val s = startDate.atTime(parseTimeOr(startTime, LocalTime.MIN))
                    val e = endDate.atTime(parseTimeOr(endTime, LocalTime.of(23, 59)))
                    if (e.isBefore(s)) {
                        error = t.errEndBeforeStart
                        return@TextButton
                    }
                } else {
                    // 短日程同样校验起止：结束早于开始会让“进行中”判定/小组件/月视图全部错乱
                    val s = parseTimeOrNull(startTime)
                    val e = parseTimeOrNull(endTime)
                    if (s != null && e != null && e.isBefore(s)) {
                        error = t.errEndBeforeStart
                        return@TextButton
                    }
                }
                val base = initial ?: AgendaEvent(
                    id = UUID.randomUUID().toString(),
                    title = titleText,
                    dateEpochDay = startDate.toEpochDay(),
                )
                onSave(
                    base.copy(
                        title = titleText,
                        dateEpochDay = startDate.toEpochDay(),
                        startTime = startTime,
                        endTime = endTime,
                        location = location.trim(),
                        note = note.trim(),
                        isLong = isLong,
                        endDateEpochDay = if (isLong) endDate.toEpochDay() else null,
                        type = type,
                        colorArgb = colorArgb,
                        isPlan = initial?.isPlan ?: asPlan,
                        repeatRule = if (isLong) "" else repeatRule,
                    )
                )
            }) { Text(t.save) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { askDelete = true }) {
                        Text(t.delete, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { if (dirtyNow()) confirmDiscard = true else onDismiss() }) { Text(t.cancel) }
            }
        },
    )

    // 有未保存修改时，关闭前二次确认（防止点遮罩/返回键误丢内容）
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(t.discardTitle) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(t.discardOk, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(t.cancel) }
            },
        )
    }

    // ---------- 日期选择（月历/年历弹窗） ----------
    if (picker == "startDate" || picker == "endDate") {
        val pickerInit = if (picker == "endDate") endDate else startDate
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = pickerInit.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { picker = "" },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val epoch = millis / 86_400_000L
                        if (picker == "endDate") endDateEpoch = epoch else startDateEpoch = epoch
                        if (isLong && endDateEpoch < startDateEpoch) endDateEpoch = startDateEpoch
                    }
                    picker = ""
                }) { Text(t.confirm) }
            },
            dismissButton = { TextButton(onClick = { picker = "" }) { Text(t.cancel) } },
        ) {
            DatePicker(state = dateState)
        }
    }

    // ---------- 时间选择（时/分滚轮弹窗） ----------
    if (picker == "startTime" || picker == "endTime") {
        TimeWheelDialog(
            initial = if (picker == "endTime") endTime else startTime,
            onDismiss = { picker = "" },
            onConfirm = { v ->
                if (picker == "endTime") endTime = v else startTime = v
                picker = ""
            },
        )
    }

    if (askDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(if (initial.isPlan) t.askDeletePlan else t.askDeleteAgenda) },
            text = { Text(t.deleteBody(initial.title)) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    onDelete(initial.id)
                }) { Text(t.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text(t.cancel) }
            },
        )
    }
}

// ---------------------------------------------------------------- 小组件

/** 「每周/隔周」但一个星期都没选：规则无效（token 形如 "weekly:"/"biweekly:"），需要提醒用户 */
internal fun isWeekdayRuleEmpty(rule: String): Boolean =
    (rule.startsWith("weekly:") || rule.startsWith("biweekly:")) &&
        rule.substringAfter(':').isBlank()

/** 只读“字段”样式，点击弹出选择器；可选清除按钮 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(label) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // 透明覆盖层接收点击（readOnly/disabled 的输入框自身不响应点击修饰符）
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { onClick() }
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 颜色圆点（编辑器色板） */
@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                }
            )
            .clickable(onClick = onClick),
    )
}

/**
 * 时间选择弹窗：支持「具体时间」（时/分滚轮）与「模糊时间」（凌晨/早晨/上午/下午/晚上/午夜）两个选项卡；
 * allowFuzzy=false 时仅提供精确时间（用于设置页的时间范围选择等场景）。
 */
@Composable
internal fun TimeWheelDialog(
    initial: String,
    allowFuzzy: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val t = LocalStrings.current
    val now = LocalTime.now()
    var fuzzyMode by remember { mutableStateOf(allowFuzzy && com.kstudio.agenda.model.FuzzyTime.isFuzzy(initial)) }
    val initH = initial.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: now.hour
    val initM = initial.substringAfter(":", "").toIntOrNull()?.coerceIn(0, 59) ?: now.minute
    val hourState = rememberLazyListState(initH)
    val minuteState = rememberLazyListState(initM)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.pickerTimeTitle) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 选项卡：具体时间 / 模糊时间（allowFuzzy=false 时仅精确时间）
                if (allowFuzzy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = !fuzzyMode,
                            onClick = { fuzzyMode = false },
                            label = { Text(t.timeExact) },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = fuzzyMode,
                            onClick = { fuzzyMode = true },
                            label = { Text(t.timeFuzzy) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (fuzzyMode) {
                    // 模糊时间：六个选项，点选即确认
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        com.kstudio.agenda.model.FuzzyTime.ORDER.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { label ->
                                    androidx.compose.material3.FilterChip(
                                        selected = initial.trim() == label,
                                        onClick = { onConfirm(label) },
                                        label = {
                                            Text(
                                                text = label,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = t.timeFuzzyHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        // 中央高亮条
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        )
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Wheel(0..23, hourState)
                            Text(
                                text = ":",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Wheel(0..59, minuteState)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!fuzzyMode) {
                TextButton(onClick = {
                    onConfirm(
                        "%02d:%02d".format(
                            hourState.firstVisibleItemIndex,
                            minuteState.firstVisibleItemIndex,
                        )
                    )
                }) { Text(t.confirm) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } },
    )
}

/** 单列滚轮：吸附滚动，选中项=居中项 */
@Composable
private fun Wheel(range: IntRange, state: LazyListState) {
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        modifier = Modifier
            .width(72.dp)
            .height(200.dp),
        contentPadding = PaddingValues(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(range.count()) { idx ->
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "%02d".format(range.first + idx),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun parseTimeOr(text: String, fallback: LocalTime): LocalTime =
    runCatching { LocalTime.parse(text) }.getOrDefault(fallback)

/** 解析 "HH:mm"（空/失败返回 null；模糊时间如“下午”返回 null，不做起止校验） */
private fun parseTimeOrNull(text: String): LocalTime? =
    if (text.isBlank()) null else runCatching { LocalTime.parse(text) }.getOrNull()
