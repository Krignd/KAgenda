@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui


import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.kstudio.agenda.ui.components.AppButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kstudio.agenda.ui.components.AppOutlinedButton as OutlinedButton
import com.kstudio.agenda.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.kstudio.agenda.ui.components.AppTextButton as TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.DocumentTextExtractor
import com.kstudio.agenda.data.LocalSmartParser
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.i18n.ImportText
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.ui.components.pageEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Unified import/review page. Local extraction never uploads; AI honors the selected provider. */
@Composable
fun DocumentImportScreen(vm: AppViewModel, initialUri: Uri? = null, initialText: String? = null, embedded: Boolean = false, onClose: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val imported by vm.importedCourses.collectAsState()
    val semester by vm.semester.collectAsState()
    val agendaAll by vm.agenda.collectAsState()
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var needsAi by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var docName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val fixedAnchor = semester?.anchorEpochDay ?: vm.importedAnchor.value?.toEpochDay() ?: settings.anchorEpochDay
    var anchorText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(fixedAnchor?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var fillWeeks by remember { mutableStateOf(false) }
    var weeksText by remember { mutableStateOf("") }
    val anchor = runCatching { LocalDate.parse(anchorText) }.getOrNull()
    val anchorValid = anchor?.dayOfWeek == java.time.DayOfWeek.MONDAY &&
        (fixedAnchor == null || anchor?.toEpochDay() == fixedAnchor)
    val suppliedWeeks = remember(weeksText) {
        weeksText.takeIf { it.matches(Regex("[0-9]{1,2}(?:-[0-9]{1,2})?(?:[,，、][0-9]{1,2}(?:-[0-9]{1,2})?)*")) }
            ?.let { raw ->
                val parts = raw.split(',', '，', '、')
                if (parts.all { part -> Course.parseWeeks(part).isNotEmpty() }) Course.parseWeeks(raw).flatMap { it.toList() }.toSet() else emptySet()
            }.orEmpty()
    }
    val review by androidx.compose.runtime.produceState(CourseImportReview(), text, needsAi) {
        kotlinx.coroutines.delay(200)
        value = withContext(Dispatchers.Default) { CourseImportReview.read(text, needsAi) }
    }
    // Do not allow a stale preview from an earlier edit to be imported during debounce.
    val currentReview = review.source == text
    val courseHits = review.courses
    val missingWeeks = courseHits.any { it.weeksRaw.isBlank() }
    val readyCourses = courseHits.map { if (it.weeksRaw.isBlank() && fillWeeks && suppliedWeeks.isNotEmpty())
        it.copy(weeksRaw = Course.encodeWeeks(suppliedWeeks)) else it }
    val conflicts = com.kstudio.agenda.data.CourseImportCodec.hasConflicts(imported + readyCourses)
    val canImport = !busy && currentReview && (!needsAi || text.trimStart().startsWith("{")) && courseHits.isNotEmpty() && review.valid && anchorValid && !conflicts &&
        (!missingWeeks || (fillWeeks && suppliedWeeks.isNotEmpty()))
    LaunchedEffect(review.firstMonday) {
        review.firstMonday?.let { anchorText = it.toString() }
    }
    LaunchedEffect(fixedAnchor) {
        if (anchorText.isBlank()) fixedAnchor?.let { anchorText = LocalDate.ofEpochDay(it).toString() }
    }

    fun convert(source: String) {
        work?.cancel()
        busy = true
        msg = ImportText.parsing
        work = scope.launch {
            try {
                val result = com.kstudio.agenda.data.CourseAiParser.convert(context, source)
                text = result
                needsAi = false
                msg = ""
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (e is com.kstudio.agenda.data.engines.EngineMissingException)
                    com.kstudio.agenda.ui.engines.EngineDownloadActivity.open(context, e.engine)
                msg = if (e is org.json.JSONException || e.message in setOf("invalid_format", "invalid_count", "invalid_slot", "invalid_monday")) ImportText.incomplete else e.message?.takeIf { it.length < 240 } ?: ImportText.failed }
            finally { busy = false }
        }
    }
    fun load(uri: Uri) {
        work?.cancel()
        text = ""
        needsAi = false
        fillWeeks = false
        busy = true
        msg = ImportText.parsing
        work = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val name = DocumentTextExtractor.displayName(context, uri)
                    val image = context.contentResolver.getType(uri).orEmpty().startsWith("image/") ||
                        DocumentTextExtractor.extensionOf(name) in setOf("png", "jpg", "jpeg", "webp", "bmp", "heic")
                    Triple(name, image, if (image) com.kstudio.agenda.data.ScreenshotTextExtractor.extract(context, uri)
                        else DocumentTextExtractor.extract(context, uri).getOrThrow())
                }
                require(result.third.length <= com.kstudio.agenda.data.CourseImportCodec.MAX_TEXT) { ImportText.tooLong }
                docName = result.first
                needsAi = result.second
                text = result.third
                msg = if (text.isBlank()) ImportText.noText else ""
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (e is com.kstudio.agenda.data.engines.EngineMissingException)
                    com.kstudio.agenda.ui.engines.EngineDownloadActivity.open(context, e.engine)
                msg = if (e is org.json.JSONException || e.message in setOf("invalid_format", "invalid_count", "invalid_slot", "invalid_monday")) ImportText.incomplete else e.message?.takeIf { it.length < 240 } ?: ImportText.failed }
            finally { busy = false }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::load) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::load) }
    val browser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(DevBrowserActivity.EXTRA_PAGE)?.let { page ->
                text = page; needsAi = true; docName = ImportText.browser; convert(page)
            }
        }
    }
    LaunchedEffect(initialUri, initialText) {
        if (initialUri != null) load(initialUri)
        else if (initialText != null) {
            if (initialText.length <= com.kstudio.agenda.data.CourseImportCodec.MAX_TEXT) { text = initialText; needsAi = false }
            else { text = ""; msg = ImportText.tooLong }
        }
    }
    var exportAsMd by remember { mutableStateOf(false) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val payload = if (exportAsMd) buildMarkdownExport(semester, imported, agendaAll) else buildCsvExport(semester, imported)
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) } ?: error("no stream") }.isSuccess
            withContext(Dispatchers.Main) { vm.message(if (ok) t.docExported else t.docExportFailed) }
        }
    }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().pageEntrance(Unit),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { ImportSection(ImportText.title, ImportText.subtitle) {
                    Text(if (settings.aiProvider == "cloud") ImportText.cloudHint else ImportText.localHint, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { vm.syncNow(); onClose() }) { Text(ImportText.portal) }
                        OutlinedButton(enabled = !busy, onClick = {
                            browser.launch(android.content.Intent(context, DevBrowserActivity::class.java).putExtra(DevBrowserActivity.EXTRA_IMPORT, true))
                        }) { Text(ImportText.browser) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text(ImportText.screenshot) }
                        OutlinedButton(enabled = !busy, onClick = { filePicker.launch(arrayOf("*/*")) }) { Text(t.docPick) }
                    }
                    if (docName.isNotBlank()) Text(docName, style = MaterialTheme.typography.labelSmall)
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error)
                } }
                item { ImportSection(ImportText.text, null) {
                    Row {
                        TextButton(enabled = !busy, onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.length <= com.kstudio.agenda.data.CourseImportCodec.MAX_TEXT) { text = pasted; needsAi = false; fillWeeks = false; msg = "" }
                            else msg = ImportText.tooLong
                        }) { Text(ImportText.paste) }
                        TextButton(enabled = text.isNotBlank(), onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }) { Text(ImportText.copy) }
                    }
                    OutlinedTextField(value = text, enabled = !busy, onValueChange = {
                        if (it.length <= com.kstudio.agenda.data.CourseImportCodec.MAX_TEXT) { text = it; msg = "" } else msg = ImportText.tooLong
                    }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && text.isNotBlank(), onClick = { convert(text) }) { Text(ImportText.ai) }
                        TextButton(enabled = !busy && currentReview && review.valid && courseHits.isNotEmpty(), onClick = {
                            text = com.kstudio.agenda.data.CourseImportCodec.encode(readyCourses, anchor?.takeIf { anchorValid })
                            needsAi = false
                        }) { Text(ImportText.standard) }
                    }
                    Text(ImportText.missing, style = MaterialTheme.typography.bodySmall)
                } }
                if (text.isNotBlank()) item { ImportSection(t.docDetected(review.events.size, courseHits.size), null) {
                    if (!review.valid) Text(ImportText.incomplete, color = MaterialTheme.colorScheme.error)
                    if (courseHits.isEmpty()) Text(ImportText.empty, style = MaterialTheme.typography.bodySmall)
                    if (conflicts) Text(ImportText.conflicts, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(anchorText, { anchorText = it }, label = { Text(ImportText.monday) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!anchorValid) Text(ImportText.invalidMonday, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (missingWeeks) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(fillWeeks, { fillWeeks = it })
                            Text(ImportText.unknownWeeks, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(weeksText, { weeksText = it }, label = { Text(ImportText.weeks) }, modifier = Modifier.fillMaxWidth())
                    }
                    readyCourses.take(500).forEach { c ->
                        Text("${c.title} · ${t.weekdayShort(c.dayOfWeek)} · ${c.periodLabel} · ${c.timeRange}\n" +
                            "${t.weeksValue(c.weeksRaw)} · ${c.room} · ${c.teacher}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Button(enabled = canImport, onClick = {
                        busy = true
                        vm.importCourses(readyCourses, anchor) { result ->
                            busy = false
                            msg = result.fold({ t.msgImportedCourses(it) }, { ImportText.failed })
                        }
                    }) { Text(ImportText.confirm) }
                    if (review.events.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && currentReview, onClick = { vm.importAgendaEvents(review.events.map { toAgendaEvent(it, false) }); onClose() }) { Text(t.docImportAgenda) }
                        OutlinedButton(enabled = !busy && currentReview, onClick = { vm.importAgendaEvents(review.events.map { toAgendaEvent(it, true) }); onClose() }) { Text(t.docImportPlan) }
                    }
                } }
                item { ImportSection(t.docImportedList(imported.size), null) {
                    imported.forEach { c -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.title} · ${t.weekdayShort(c.dayOfWeek)} · ${c.periodLabel} · ${t.weeksValue(c.weeksRaw)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.deleteImportedCourse(c.id) }) { Icon(Icons.Filled.Close, contentDescription = t.cancel) }
                    } }
                    if (imported.isNotEmpty()) OutlinedButton(onClick = { vm.clearImportedCourses() }) { Text(t.docClearImported) }
                } }
                item { ImportSection(t.docExport, t.docExportHint) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportAsMd = false; createDoc.launch("kagenda_export.csv") }) { Text(t.docExportCsv) }
                        OutlinedButton(onClick = { exportAsMd = true; createDoc.launch("kagenda_export.md") }) { Text(t.docExportMd) }
                    }
                } }
            }
    }
    if (embedded) {
        content(PaddingValues(0.dp))
    } else {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            Scaffold(modifier = Modifier.fillMaxSize(),
                containerColor = com.kstudio.agenda.ui.theme.overlayBackgroundColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                topBar = { SettingsSubHeader(ImportText.title, onBack = onClose) }) { padding -> content(padding) }
        }
    }
}

@Composable
private fun ImportSection(title: String, subtitle: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    com.kstudio.agenda.ui.components.CollapsibleSectionCard(title, subtitle,
        expanded = expanded, onToggle = { expanded = !expanded }, content = content)
}

internal data class CourseImportReview(
    val source: String = "", val courses: List<Course> = emptyList(),
    val events: List<AiSkills.AiOp.Add> = emptyList(), val firstMonday: LocalDate? = null, val valid: Boolean = true,
) {
    companion object {
        fun read(text: String, needsAi: Boolean): CourseImportReview {
            if (text.isBlank()) return CourseImportReview(source = text)
            val json = text.trimStart().startsWith("{") || text.trimStart().startsWith("[") || text.trimStart().startsWith("```")
            if (json) return runCatching {
                val decoded = com.kstudio.agenda.data.CourseImportCodec.decode(text)
                CourseImportReview(text, decoded.courses, firstMonday = decoded.firstMonday, valid = decoded.issues.isEmpty())
            }.getOrElse { CourseImportReview(source = text, valid = false) }
            if (needsAi) return CourseImportReview(source = text)
            val courses = LocalSmartParser.parseCourses(text)
            return CourseImportReview(text, courses, if (courses.isEmpty()) LocalSmartParser.parseEvents(text, LocalDate.now()) else emptyList(),
                valid = courses.size <= 500 && LocalSmartParser.invalidCourseRows(text) == 0)
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
internal fun buildCsvExport(
    semester: com.kstudio.agenda.model.SemesterSchedule?,
    imported: List<Course>,
): String {
    val courses = LinkedHashMap<String, Course>()
    semester?.weeks?.values?.forEach { list -> list.forEach { courses[it.id] = it } }
    imported.forEach { courses[it.id] = it }
    val sb = StringBuilder()
    sb.append("课程名,星期,节次,时间,周次,教室,教师,课程号\n")
    for (c in courses.values.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))) {
        sb.append(csvCell(c.title)).append(',')
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
internal fun buildMarkdownExport(
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
            sb.append("| ").append(mdCell(c.title))
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
