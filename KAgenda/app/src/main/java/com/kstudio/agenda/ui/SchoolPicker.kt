@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import com.kstudio.agenda.ui.theme.LocalGlassEnabled

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.model.SchoolDirectory
import com.kstudio.agenda.data.SchoolCatalog
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.AcademicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SchoolSupport(school: School, modifier: Modifier = Modifier) {
    val t = LocalStrings.current
    Text(
        com.kstudio.agenda.i18n.AcademicText.schoolSupportLabel(school, t),
        modifier, color = if (school.supported || SchoolCatalog.knownSystemTypes(school).isNotEmpty())
            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
    )
}

/** 全屏、惰性列表；目录收录和脚本适配状态分别展示。 */
@Composable
internal fun SchoolPicker(selectedId: String, onSelect: (School) -> Unit, onDismiss: () -> Unit, onAdd: (() -> Unit)? = null) {
    val t = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickerSettings by remember(context) { SettingsStore.settingsFlow(context.applicationContext) }
        .collectAsState(initial = null)
    val hideUndetermined = pickerSettings?.hideUndeterminedSchools ?: false
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val all = remember { Schools.all() }
    val entries by produceState<List<SchoolDirectory.Entry>?>(null, all) {
        value = withContext(Dispatchers.Default) { SchoolDirectory.sorted(all, SchoolCatalog.nameIndex(context.applicationContext)) }
    }
    val listing = remember(query, entries, hideUndetermined) {
        SchoolDirectory.filter(entries.orEmpty().filterNot { hideUndetermined && AcademicText.isUndetermined(it.school) }, query)
    }
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    var flashLetter by remember { mutableStateOf("") }
    var jumpSequence by remember { mutableIntStateOf(0) }
    val activeLetter by remember(listing) { derivedStateOf {
        when (val row = listing.rows.getOrNull(listState.firstVisibleItemIndex)) {
            is SchoolDirectory.Row.Header -> row.letter
            is SchoolDirectory.Row.Item -> row.entry.spelling.letter
            else -> ""
        }
    } }
    LaunchedEffect(query, entries, hideUndetermined) { listState.scrollToItem(0) }
    LaunchedEffect(jumpSequence) { delay(650); flashLetter = "" }
    val jump: (String) -> Unit = { letter ->
        listing.positions[letter]?.let { position ->
            keyboard?.hide()
            flashLetter = letter; jumpSequence++
            jumpJob?.cancel()
            jumpJob = scope.launch { listState.scrollToItem(position) }
        }
    }
    // Compose 1.7 在 usePlatformDefaultWidth=false 时按整屏重新测量，忽略键盘缩小后的约束。
    // 保留原生窗口测量，并在下方显式设为 MATCH_PARENT，兼顾全屏和输入法避让。
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val previousInputMode = window?.attributes?.softInputMode
            val previousGravity = window?.attributes?.gravity
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window?.setGravity(Gravity.TOP or Gravity.START)
            window?.setDimAmount(0f)
            onDispose {
                previousInputMode?.let { window.setSoftInputMode(it) }
                previousGravity?.let { window.setGravity(it) }
            }
        }
        Surface(
            // 窗口始终铺满屏幕；键盘只通过 Insets 压缩内容，不能反过来固定窗口高度。
            Modifier.fillMaxSize(),
            color = com.kstudio.agenda.ui.theme.overlayBackgroundColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Scaffold(modifier = Modifier.imePadding(), containerColor = if (LocalGlassEnabled.current) androidx.compose.ui.graphics.Color.Transparent else com.kstudio.agenda.ui.theme.overlayBackgroundColor(), topBar = {
                TopAppBar(title = { Text(t.secSchool) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t.cancel) } })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text(t.schoolSearch) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().toggleable(value = hideUndetermined, role = Role.Checkbox,
                        onValueChange = { hidden -> scope.launch { SettingsStore.setHideUndeterminedSchools(context.applicationContext, hidden) } }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hideUndetermined, onCheckedChange = null)
                        Text(AcademicText.hideUndeterminedSchools, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(t.schoolCatalogNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        onAdd?.let { TextButton(onClick = it) { Text(t.schoolAdd) } }
                        TextButton(onClick = { source = true }) { Text(t.warehouseSource) }
                    }
                    if (entries == null) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp))
                    else if (listing.rows.isEmpty()) Text(t.noSchoolFound, Modifier.padding(16.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        itemsIndexed(listing.rows, key = { _, row -> when(row) {
                            is SchoolDirectory.Row.Header -> "section_${row.letter}"
                            is SchoolDirectory.Row.Item -> "school_${row.entry.school.id}"
                        } }) { _, row ->
                            if (row is SchoolDirectory.Row.Header) {
                                Text(row.letter, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
                            } else if (row is SchoolDirectory.Row.Item) {
                            val school = row.entry.school
                            Row(Modifier.fillMaxWidth().clickable { onSelect(school) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SchoolLogo(school, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(school.name, style = MaterialTheme.typography.bodyMedium)
                                    if (school.undergraduateOnly) Text(t.undergraduates, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
                                    if (school.province.isNotBlank()) Text(school.province, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 104.dp)) {
                                    SchoolSupport(school)
                                    if (selectedId == school.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()
                            }
                        }
                    }
                    if (!listing.searching) SchoolAlphabetRail(listing.positions, activeLetter, jump, Modifier.fillMaxHeight())
                    }
                    if (flashLetter.isNotBlank()) Surface(Modifier.align(Alignment.Center).size(76.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
                        Box(contentAlignment = Alignment.Center) { Text(flashLetter, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    }
                }
            }
        }
    }
    if (source) WarehouseLicenseDialog { source = false }
}

/** 同时支持点按与纵向滑动；缺少对应学校的字母不产生无效跳转。 */
@Composable
private fun SchoolAlphabetRail(positions: Map<String, Int>, selected: String, onJump: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalStrings.current
    val letters = remember(positions) { SchoolDirectory.alphabet.filter { it != "#" || it in positions } }
    val latestJump by rememberUpdatedState(onJump)
    BoxWithConstraints(modifier.width(30.dp).semantics { contentDescription = t.schoolAlphabetIndex }) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val fontSize = with(density) { minOf(11.sp.toPx(), heightPx / letters.size * .72f).coerceAtLeast(1f).toSp() }
        Column(Modifier.fillMaxSize().pointerInput(positions, letters, heightPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun choose(y: Float) {
                    val index = (y / heightPx.coerceAtLeast(1f) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    if (letters[index] in positions) latestJump(letters[index])
                }
                down.consume(); choose(down.position.y)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) { choose(change.position.y); change.consume() }
                } while (event.changes.any { it.id == down.id && it.pressed })
            }
        }) {
            letters.forEach { letter ->
                val enabled = letter in positions
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().semantics { contentDescription = t.schoolIndexJump(letter) }
                    .clickable(enabled = enabled, role = Role.Button) { latestJump(letter) }, contentAlignment = Alignment.Center) {
                    Text(letter, fontSize = fontSize, lineHeight = fontSize, maxLines = 1,
                        color = if (letter == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) .85f else .25f))
                }
            }
        }
    }
}
