package com.kstudio.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.AiAssistant
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.RepeatRules
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AI 助手：粘贴一段文字 → 解析为「新增 / 修改 / 删除」操作 → 预览（可逐条修改/删除）→ 全部执行。
 * 入口：顶栏 DeepSeek 图标按钮、可拖拽悬浮按钮、常驻通知入口、系统悬浮球。
 */
@Composable
fun AiQuickAddDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var ops by remember { mutableStateOf<List<AiSkills.AiOp>>(emptyList()) }
    var editIndex by remember { mutableStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.qaTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        // 修改文字后旧识别结果失效：防止“改了内容却把旧结果执行了”的误操作
                        if (ops.isNotEmpty()) ops = emptyList()
                    },
                    placeholder = {
                        Text(
                            text = t.qaHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ops.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ops.forEachIndexed { index, op ->
                            AiOpRow(
                                op = op,
                                onClick = { editIndex = index },
                                onRemove = { ops = ops.filterIndexed { i, _ -> i != index } },
                            )
                        }
                    }
                }
                if (msg.isNotBlank()) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank() || busy) return@TextButton
                    busy = true
                    msg = t.aiRunning
                    ops = emptyList()
                    scope.launch {
                        try {
                            val key = SettingsStore.effectiveAiKey(context)
                            if (key.isNullOrBlank()) {
                                msg = t.aiNeedKey
                                return@launch
                            }
                            val model = SettingsStore.effectiveAiModel(context)
                            val reply = AiClient.chat(
                                apiKey = key,
                                model = model,
                                systemPrompt = AiSkills.assistantSystemPrompt,
                                userPrompt = AiSkills.assistantUserPrompt(
                                    text,
                                    LocalDate.now(),
                                    AiAssistant.contextLines(vm.agenda.value),
                                ),
                            )
                            val list = AiSkills.parseOpsReply(reply)
                            if (list.isEmpty()) {
                                msg = t.qaNothing
                            } else {
                                ops = list
                                msg = ""
                            }
                        } catch (e: Throwable) {
                            msg = (e.message ?: t.parseFail).take(60)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = text.isNotBlank() && !busy,
            ) { Text(t.qaParse) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val list = ops
                    if (list.isEmpty()) return@TextButton
                    // 防误触连点：先清空待执行列表，双击也不会重复执行
                    ops = emptyList()
                    vm.applyAiOps(list)
                    onDismiss()
                },
                enabled = ops.isNotEmpty(),
            ) { Text(t.qaConfirm) }
        },
    )

    if (editIndex in ops.indices) {
        AiOpEditDialog(
            op = ops[editIndex],
            onDismiss = { editIndex = -1 },
            onSave = { edited ->
                ops = ops.toMutableList().also { it[editIndex] = edited }
                editIndex = -1
            },
        )
    }
}

/** 操作预览行：动作徽标（添加/修改/删除）+ 内容 + 移除按钮；点击行可修改 */
@Composable
private fun AiOpRow(op: AiSkills.AiOp, onClick: () -> Unit, onRemove: () -> Unit) {
    val t = LocalStrings.current
    val (badge, color) = when (op) {
        is AiSkills.AiOp.Add -> t.opAdd to MaterialTheme.colorScheme.primary
        is AiSkills.AiOp.Update -> t.opUpdate to MaterialTheme.colorScheme.tertiary
        is AiSkills.AiOp.Delete -> t.opDelete to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = opSummary(op, t),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 预览行摘要文案（本地拼接，字段已是用户可读文本） */
private fun opSummary(op: AiSkills.AiOp, t: com.kstudio.agenda.i18n.AppStrings): String = when (op) {
    is AiSkills.AiOp.Add -> buildString {
        append(op.item.title)
        op.item.date?.let { append(" · ${it.monthValue}/${it.dayOfMonth}") }
        if (op.item.startTime.isNotBlank()) append(" ").append(op.item.startTime)
        if (op.item.endTime.isNotBlank()) append("-").append(op.item.endTime)
        if (op.item.location.isNotBlank()) append(" · ").append(op.item.location)
        t.repeatLabel(op.item.repeat).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        append(if (op.item.isPlan) "（计划）" else "（日程）")
    }
    is AiSkills.AiOp.Update -> buildString {
        append("「").append(op.matchTitle).append("」")
        op.set.title?.let { append(" → ").append(it) }
        op.set.date?.let { append(" · ").append(it.monthValue).append("/").append(it.dayOfMonth) }
        op.set.startTime?.let { append(" ").append(it) }
        op.set.endTime?.let { append("-").append(it) }
        op.set.location?.let { append(" · ").append(it) }
        op.set.repeat?.let { r -> append(" · ").append(t.repeatLabel(r).ifBlank { t.repeatNone }) }
    }
    is AiSkills.AiOp.Delete -> "「${op.matchTitle}」"
}

/** 单条操作的修改对话框：可编辑“目标/内容”，确认后写回预览列表 */
@Composable
private fun AiOpEditDialog(
    op: AiSkills.AiOp,
    onDismiss: () -> Unit,
    onSave: (AiSkills.AiOp) -> Unit,
) {
    val t = LocalStrings.current
    val add = op as? AiSkills.AiOp.Add
    val upd = op as? AiSkills.AiOp.Update
    val del = op as? AiSkills.AiOp.Delete

    var title by remember { mutableStateOf(add?.item?.title.orEmpty()) }
    var isPlan by remember { mutableStateOf(add?.item?.isPlan == true) }
    var matchTitle by remember { mutableStateOf(upd?.matchTitle ?: del?.matchTitle.orEmpty()) }
    var matchDate by remember { mutableStateOf(upd?.matchDate?.toString() ?: del?.matchDate?.toString().orEmpty()) }
    var date by remember { mutableStateOf(add?.item?.date?.toString().orEmpty()) }
    var start by remember { mutableStateOf(add?.item?.startTime.orEmpty()) }
    var end by remember { mutableStateOf(add?.item?.endTime.orEmpty()) }
    var loc by remember { mutableStateOf(add?.item?.location.orEmpty()) }
    var nTitle by remember { mutableStateOf(upd?.set?.title.orEmpty()) }
    var nDate by remember { mutableStateOf(upd?.set?.date?.toString().orEmpty()) }
    var nStart by remember { mutableStateOf(upd?.set?.startTime.orEmpty()) }
    var nEnd by remember { mutableStateOf(upd?.set?.endTime.orEmpty()) }
    var nLoc by remember { mutableStateOf(upd?.set?.location.orEmpty()) }
    var rp by remember { mutableStateOf(add?.item?.repeat.orEmpty()) }
    var nRp by remember { mutableStateOf(upd?.set?.repeat?.takeIf { it.isNotBlank() }.orEmpty()) }

    fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (op) {
                    is AiSkills.AiOp.Add -> t.opAdd
                    is AiSkills.AiOp.Update -> t.opUpdate
                    is AiSkills.AiOp.Delete -> t.opDelete
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (add != null) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(t.fieldTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text(t.fieldDate) }, placeholder = { Text("2026-09-20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text(t.fieldStartTime) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text(t.fieldEndTime) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text(t.fieldLocation) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rp, onValueChange = { rp = it }, label = { Text(t.repeatSection) }, placeholder = { Text(t.repeatFieldHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { isPlan = !isPlan }) { Text(if (isPlan) t.qaPlanTag else t.qaAgendaTag) }
                } else {
                    OutlinedTextField(value = matchTitle, onValueChange = { matchTitle = it }, label = { Text(t.opMatchTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = matchDate, onValueChange = { matchDate = it }, label = { Text(t.opMatchDate) }, placeholder = { Text("2026-09-20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (del == null) {
                        OutlinedTextField(value = nTitle, onValueChange = { nTitle = it }, label = { Text(t.opNewTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = nDate, onValueChange = { nDate = it }, label = { Text(t.opNewDate) }, placeholder = { Text("2026-09-20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = nStart, onValueChange = { nStart = it }, label = { Text(t.opNewStart) }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = nEnd, onValueChange = { nEnd = it }, label = { Text(t.opNewEnd) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = nLoc, onValueChange = { nLoc = it }, label = { Text(t.opNewLocation) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = nRp, onValueChange = { nRp = it }, label = { Text(t.repeatSection) }, placeholder = { Text(t.repeatFieldHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val edited: AiSkills.AiOp = when (op) {
                    is AiSkills.AiOp.Add -> AiSkills.AiOp.Add(
                        op.item.copy(
                            title = title.trim().ifBlank { op.item.title },
                            isPlan = isPlan,
                            date = parseDate(date) ?: op.item.date,
                            startTime = start.trim(),
                            endTime = end.trim(),
                            location = loc.trim(),
                            repeat = if (rp.isBlank()) "" else (RepeatRules.parse(rp) ?: op.item.repeat),
                        )
                    )
                    is AiSkills.AiOp.Update -> AiSkills.AiOp.Update(
                        matchTitle = matchTitle.trim().ifBlank { op.matchTitle },
                        matchDate = parseDate(matchDate),
                        set = AiSkills.AiSet(
                            title = nTitle.trim().takeIf { it.isNotBlank() },
                            date = parseDate(nDate),
                            startTime = nStart.trim().takeIf { it.isNotBlank() },
                            endTime = nEnd.trim().takeIf { it.isNotBlank() },
                            location = nLoc.trim().takeIf { it.isNotBlank() },
                            note = op.set.note,
                            repeat = if (nRp.isBlank()) op.set.repeat else (RepeatRules.parse(nRp) ?: op.set.repeat),
                        ),
                    )
                    is AiSkills.AiOp.Delete -> AiSkills.AiOp.Delete(
                        matchTitle = matchTitle.trim().ifBlank { op.matchTitle },
                        matchDate = parseDate(matchDate),
                    )
                }
                onSave(edited)
            }) { Text(t.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } },
    )
}
