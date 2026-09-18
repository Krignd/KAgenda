package com.kstudio.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.AgendaTypes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * AI 快速添加：粘贴一段文字（可含多个日程/计划）→ DeepSeek 多条目解析 → 预览 → 批量添加。
 * 入口：顶栏 DeepSeek 图标按钮、可拖拽悬浮按钮。
 */
@Composable
fun AiQuickAddDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<AiSkills.AiItem>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.qaTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        // 修改文字后旧识别结果失效：防止“改了内容却把旧结果加进去”的误操作
                        if (items.isNotEmpty()) items = emptyList()
                    },
                    placeholder = {
                        Text(
                            text = t.qaHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = items.joinToString("\n") { item ->
                            val tag = if (item.isPlan) t.qaPlanTag else t.qaAgendaTag
                            buildString {
                                append("[").append(tag).append("] ").append(item.title)
                                item.date?.let { d -> append(" · ${d.monthValue}/${d.dayOfMonth}") }
                                if (item.startTime.isNotBlank()) append(" ").append(item.startTime)
                                if (item.location.isNotBlank()) append(" · ").append(item.location)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    items = emptyList()
                    scope.launch {
                        try {
                            val key = SettingsStore.aiKey(context)
                            if (key.isNullOrBlank()) {
                                msg = t.aiNeedKey
                                return@launch
                            }
                            val model = SettingsStore.read(context).aiModel
                            val reply = AiClient.chat(
                                apiKey = key,
                                model = model,
                                systemPrompt = AiSkills.parseItemsSystemPrompt,
                                userPrompt = AiSkills.userPrompt(text, LocalDate.now()),
                            )
                            val list = AiSkills.parseItemsReply(reply)
                            if (list.isEmpty()) {
                                msg = t.qaNothing
                            } else {
                                items = list
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
                    val list = items
                    if (list.isEmpty()) return@TextButton
                    // 防误触连点：先清空待添加列表，双击也不会重复保存
                    items = emptyList()
                    vm.saveAgendaEvents(buildAgendaEvents(list))
                    onDismiss()
                },
                enabled = items.isNotEmpty(),
            ) { Text(t.qaConfirm) }
        },
    )
}

/** 由 AI 识别结果构建本地日程/计划事件（AI 快速添加对话框与系统悬浮球共用） */
fun buildAgendaEvents(items: List<AiSkills.AiItem>): List<AgendaEvent> = items.map { item ->
    AgendaEvent(
        id = UUID.randomUUID().toString(),
        title = item.title,
        dateEpochDay = (item.date ?: LocalDate.now()).toEpochDay(),
        startTime = item.startTime,
        endTime = item.endTime,
        location = item.location,
        note = item.note,
        type = item.type.takeIf { key -> key in AgendaTypes.ORDER } ?: "",
        isPlan = item.isPlan,
    )
}
