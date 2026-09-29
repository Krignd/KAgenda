package com.kstudio.agenda.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.DocumentTextExtractor
import com.kstudio.agenda.data.LocalSmartParser
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 「文档导入」页：本机读取 Word / Excel / PPT / PDF / 文本文件，
 * 自动识别课程表、日程与计划，并可直接导入到应用中。
 *
 * 全部处理在本机完成（不联网、不上传）。其他应用通过「用 K日程 打开」传入的文件
 * 也会进入本页（见 MainActivity 的 ACTION_VIEW / ACTION_SEND 处理）。
 */
@Composable
fun DocumentImportScreen(
    vm: AppViewModel,
    initialUri: Uri? = null,
    onClose: () -> Unit,
) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val imported by vm.importedCourses.collectAsState()
    val semester by vm.semester.collectAsState()
    val agendaAll by vm.agenda.collectAsState()

    var docName by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    // 第 1 教学周周一：默认沿用当前学期的锚点，未同步时取本周周一
    var anchor by remember {
        mutableStateOf(
            vm.importedAnchor.value
                ?: settings.anchorEpochDay?.let { LocalDate.ofEpochDay(it) }
                ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        )
    }

    fun load(u: Uri) {
        busy = true
        msg = t.docReading
        docName = DocumentTextExtractor.displayName(context, u)
        scope.launch {
            val result = withContext(Dispatchers.IO) { DocumentTextExtractor.extract(context, u) }
            result.onSuccess {
                text = it
                msg = ""
                if (it.isBlank()) msg = t.docNoText
            }.onFailure { e ->
                val m = e.message.orEmpty()
                DocumentTextExtractor.logFailure(docName, e)
                msg = when {
                    m.startsWith("UNSUPPORTED:") -> t.docUnsupportedExt(m.substringAfter(':').trim())
                    m.isBlank() -> t.docReadFail(t.docNoText)
                    else -> t.docReadFail(m)
                }
            }
            busy = false
        }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { load(it) }
    }

    // 识别结果（随文本变化重新计算）：日程/计划候选 与 课程候选
    val eventOps = remember(text) {
        if (text.isBlank()) emptyList() else LocalSmartParser.parseEvents(text, LocalDate.now())
    }
    val courseHits = remember(text) {
        if (text.isBlank()) emptyList() else LocalSmartParser.parseCourses(text)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { load(it) } }

    // 导出（把当前课表/日程写成文档）
    var exportAsMd by remember { mutableStateOf(false) }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val payload = if (exportAsMd) buildMarkdownExport(semester, imported, agendaAll)
        else buildCsvExport(semester, imported)
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("no stream")
        }.isSuccess
        vm.message(if (ok) t.docExported else t.docExportFailed)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                text = t.docTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = null)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(t.docTitle, t.docLocalOnly) {
                    Text(
                        text = t.docIntro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text(t.docPick) }
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = t.docOpenHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (docName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = docName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (msg.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (text.isNotBlank()) {
                item {
                    SectionCard(t.docTextLabel, null) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    SectionCard(
                        t.docDetected(eventOps.size, courseHits.size),
                        null,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    vm.importAgendaEvents(eventOps.map { toAgendaEvent(it, false) })
                                    onClose()
                                },
                                enabled = eventOps.isNotEmpty(),
                            ) { Text(t.docImportAgenda) }
                            OutlinedButton(
                                onClick = {
                                    vm.importAgendaEvents(eventOps.map { toAgendaEvent(it, true) })
                                    onClose()
                                },
                                enabled = eventOps.isNotEmpty(),
                            ) { Text(t.docImportPlan) }
                        }
                        if (courseHits.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = t.docAnchorLabel(
                                        anchor.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { anchor = anchor.minusWeeks(1) }) { Text("－") }
                                TextButton(onClick = { anchor = anchor.plusWeeks(1) }) { Text("＋") }
                            }
                            Text(
                                text = t.docAnchorHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            courseHits.take(10).forEach { c ->
                                Text(
                                    text = "${c.title} · ${t.weekdayShort(c.dayOfWeek)} · " +
                                        "${c.periodLabel} · ${c.timeRange}" +
                                        (if (c.room.isNotBlank()) " · ${c.room}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (courseHits.size > 10) {
                                Text(
                                    text = "…共 ${courseHits.size} 门",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                vm.importCourses(courseHits, anchor)
                            }) { Text(t.docImportCourses) }
                        }
                    }
                }
            }

            // 已导入课程（可逐条删除 / 清空）
            item {
                SectionCard(t.docImportedList(imported.size), null) {
                    if (imported.isEmpty()) {
                        Text(
                            text = t.emptyPlansHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Box {
                            Column {
                                imported.forEach { c ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${c.title} · ${t.weekdayShort(c.dayOfWeek)} · ${c.periodLabel}" +
                                                if (c.weeksRaw.isNotBlank()) " · ${t.weeksValue(c.weeksRaw)}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { vm.deleteImportedCourse(c.id) },
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { vm.clearImportedCourses() }) {
                            Text(t.docClearImported)
                        }
                    }
                }
            }

            // 导出为文档
            item {
                SectionCard(t.docExport, t.docExportHint) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            exportAsMd = false
                            createDoc.launch("kagenda_export.csv")
                        }) { Text(t.docExportCsv) }
                        OutlinedButton(onClick = {
                            exportAsMd = true
                            createDoc.launch("kagenda_export.md")
                        }) { Text(t.docExportMd) }
                    }
                }
            }
        }
    }
}

/** 本地识别结果 → 本地日程/计划条目 */
private fun toAgendaEvent(op: AiSkills.AiOp.Add, isPlan: Boolean): AgendaEvent {
    val item = op.item
    return AgendaEvent(
        id = UUID.randomUUID().toString(),
        title = item.title,
        dateEpochDay = (item.date ?: LocalDate.now()).toEpochDay(),
        startTime = item.startTime,
        endTime = item.endTime,
        location = item.location,
        note = item.note,
        type = item.type,
        isPlan = isPlan,
        repeatRule = item.repeat,
    )
}

/** 导出 CSV：课程表（含导入课程） */
private fun buildCsvExport(
    semester: com.kstudio.agenda.model.SemesterSchedule?,
    imported: List<Course>,
): String {
    val courses = LinkedHashMap<String, Course>()
    semester?.weeks?.values?.forEach { list -> list.forEach { courses[it.id] = it } }
    imported.forEach { courses[it.id] = it }
    val sb = StringBuilder()
    sb.append("课程名,星期,节次,时间,周次,教室,教师,课程号\n")
    for (c in courses.values.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))) {
        sb.append(csvCell(markedTitle(c))).append(',')
            .append(c.dayOfWeek).append(',')
            .append(csvCell(c.periodLabel)).append(',')
            .append(csvCell(c.timeRange)).append(',')
            .append(csvCell(c.weeksRaw)).append(',')
            .append(csvCell(c.room)).append(',')
            .append(csvCell(c.teacher)).append(',')
            .append(csvCell(c.code)).append('\n')
    }
    return sb.toString()
}

/** 导出 Markdown：课程表 + 日程 + 计划 */
private fun buildMarkdownExport(
    semester: com.kstudio.agenda.model.SemesterSchedule?,
    imported: List<Course>,
    agenda: List<AgendaEvent>,
): String {
    val sb = StringBuilder()
    sb.append("# K日程导出\n\n")
    sb.append("生成时间：").append(LocalDate.now()).append("\n\n")
    sb.append("## 课程\n\n")
    val courses = LinkedHashMap<String, Course>()
    semester?.weeks?.values?.forEach { list -> list.forEach { courses[it.id] = it } }
    imported.forEach { courses[it.id] = it }
    if (courses.isEmpty()) {
        sb.append("（无）\n\n")
    } else {
        sb.append("| 课程 | 星期 | 节次 | 时间 | 周次 | 教室 | 教师 |\n")
        sb.append("| --- | --- | --- | --- | --- | --- | --- |\n")
        for (c in courses.values.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))) {
            sb.append("| ").append(mdCell(markedTitle(c)))
                .append(" | ").append(c.dayOfWeek)
                .append(" | ").append(mdCell(c.periodLabel))
                .append(" | ").append(mdCell(c.timeRange))
                .append(" | ").append(mdCell(c.weeksRaw))
                .append(" | ").append(mdCell(c.room))
                .append(" | ").append(mdCell(c.teacher)).append(" |\n")
        }
        sb.append('\n')
    }
    sb.append("## 日程与计划\n\n")
    if (agenda.isEmpty()) {
        sb.append("（无）\n")
    } else {
        for (e in agenda.sortedWith(compareBy({ it.dateEpochDay }, { it.startTime }))) {
            sb.append("- ").append(e.date)
                .append(' ').append(if (e.isPlan) "[计划] " else "[日程] ")
                .append(e.title)
                .append(if (e.timeLabel.isNotBlank()) " ${e.timeLabel}" else "")
                .append(if (e.location.isNotBlank()) " @${e.location}" else "")
                .append('\n')
        }
    }
    return sb.toString()
}

private fun csvCell(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

private fun mdCell(s: String): String = s.replace("|", "\\|").replace("\n", " ")
