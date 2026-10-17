@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.kstudio.agenda.data.*
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.*
import com.kstudio.agenda.ui.theme.KAgendaTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** 独立于自有适配引擎的社区协议宿主。脚本固定到已核对协议与许可的仓库提交，用户主动执行。 */
class WarehouseImportActivity : ComponentActivity() {
    private var web: WebView? = null
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var page by mutableStateOf("")
    private var batch by mutableStateOf<WarehousePayload.Batch?>(null)
    private var dialog by mutableStateOf<Request?>(null)
    private var adapterIndex by mutableIntStateOf(0)
    private var token = ""
    private var courses = ""
    private var times = ""
    private var config = ""
    private var blockedMessage = ""
    private var timeout: Job? = null
    private var icon: Bitmap? = null
    private val school by lazy { Schools.of(intent.getStringExtra("schoolId") ?: Schools.currentId) }
    private val adapters by lazy { SchoolCatalog.adapters(school) }
    private val t get() = AppText.current
    private data class Request(val id: String, val action: String, val data: JSONObject, val token: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (adapters.isEmpty()) { finish(); return }
        setContent {
            val language by AppText.state.collectAsState()
            CompositionLocalProvider(LocalStrings provides AppText.stringsFor(language)) {
                KAgendaTheme { Content() }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun browser(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), "_kagendaWarehouse")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme !in setOf("http", "https")
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                page = url.orEmpty()
                if (busy) fail(t.warehouseFailed)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status = t.warehouseFailed
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedIcon(view: WebView, bitmap: Bitmap?) { icon = bitmap }
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                // 仓库脚本正常使用异步桥接；普通网页仍保留系统 WebView 对话框。
                return super.onJsAlert(view, url, message, result)
            }
        }
        loadUrl(adapters[adapterIndex].url)
    }.also { web = it }

    private fun runScript() {
        if (busy) return
        val adapter = adapters[adapterIndex]
        if (adapter.combo) { status = t.warehouseUnsupportedTime; return }
        token = UUID.randomUUID().toString()
        courses = ""; times = ""; config = ""; blockedMessage = ""
        batch = null; dialog = null; busy = true; status = t.warehouseBusy
        runCatching {
            val bridge = assets.open("shiguang/bridge.js").bufferedReader().use { it.readText() }.replace("__TOKEN__", JSONObject.quote(token))
            val script = assets.open("shiguang/scripts/" + adapter.path).bufferedReader().use { it.readText() }
            val runner = assets.open("shiguang/runner.js").bufferedReader().use { it.readText() }
            web?.evaluateJavascript(WarehouseScriptRunner.wrap(bridge, script, runner), null)
        }.onFailure { fail(t.warehouseFailed) }
        timeout?.cancel()
        timeout = lifecycleScope.launch { delay(120_000); if (busy) fail(t.warehouseFailed) }
    }

    inner class Bridge {
        @JavascriptInterface fun dispatch(raw: String) {
            if (raw.length > 2_000_000) return
            val request = runCatching { JSONObject(raw) }.getOrNull() ?: return
            runOnUiThread {
                if (!busy || request.optString("token") != token) return@runOnUiThread
                val id = request.optString("id")
                if (id.isNotEmpty() && !id.matches(Regex("sg_[0-9]{1,8}"))) return@runOnUiThread
                val action = request.optString("action")
                val data = request.optJSONObject("payload") ?: JSONObject()
                when (action) {
                    "alert", "prompt", "selection" -> {
                        if (dialog != null) reply(id, false, t.warehouseBusy) else dialog = Request(id, action, data, token)
                    }
                    "courses" -> { courses = data.optString("data"); reply(id, true, true) }
                    "times" -> { times = data.optString("data"); reply(id, true, true) }
                    "config" -> runCatching { WarehousePayload.mergeConfig(config, data.optString("data")) }
                        .onSuccess { config = it; reply(id, true, true) }
                        .onFailure { blockedMessage = t.warehouseInvalid; reply(id, false, t.warehouseInvalid) }
                    "combo" -> { blockedMessage = t.warehouseUnsupportedTime; reply(id, false, t.warehouseUnsupportedTime) }
                    "toast" -> status = data.optString("text").take(500)
                    "complete" -> complete()
                    else -> reply(id, false, t.warehouseInvalid)
                }
            }
        }
    }

    private fun reply(id: String, ok: Boolean, value: Any?) {
        if (!busy || id.isEmpty()) return
        val encoded = JSONArray().put(value ?: JSONObject.NULL).toString().removePrefix("[").removeSuffix("]")
        web?.evaluateJavascript("window.__kagendaSgReply && window.__kagendaSgReply(${JSONObject.quote(id)},$ok,$encoded);", null)
    }
    private fun answer(request: Request, value: Any?) {
        if (request.token != token || dialog !== request) return
        dialog = null; reply(request.id, true, value)
    }
    private fun fail(text: String) {
        busy = false; token = ""; dialog = null; batch = null; timeout?.cancel(); status = text
    }
    private fun complete() {
        if (blockedMessage.isNotBlank()) { fail(blockedMessage); return }
        runCatching { WarehousePayload.parse(courses, times, config) }.onSuccess {
            batch = it; busy = false; token = ""; dialog = null; timeout?.cancel(); status = t.warehouseReady
        }.onFailure { fail(if (it is WarehousePayload.UnsupportedTime) t.warehouseUnsupportedTime else t.warehouseInvalid) }
    }

    private fun save(result: WarehousePayload.Batch, anchor: LocalDate) {
        if (busy || Schools.currentId != school.id) return
        busy = true
        lifecycleScope.launch {
            runCatching {
                val label = "${school.name} · $anchor"
                // 所有脚本字段已校验；确认预览后才写入，保留原教务数据与修改覆盖层的分离。
                val semester = SemesterSchedule(label, anchor.toEpochDay(), (1..result.totalWeeks).associateWith { week -> result.courses.filter { it.occursInWeek(week) } }, System.currentTimeMillis())
                if (result.times.isNotEmpty()) {
                    SettingsStore.setPeriodTimes(this@WarehouseImportActivity, PeriodTimes.encode(result.times))
                    PeriodTimes.applyCustom(result.times)
                }
                check(ScheduleCache.save(this@WarehouseImportActivity, semester))
                SettingsStore.setSemesterAnchor(this@WarehouseImportActivity, label, anchor.toEpochDay())
                SettingsStore.setLastSync(this@WarehouseImportActivity, semester.fetchedAtMillis)
                icon?.let { bmp -> runCatching { withContext(Dispatchers.IO) { SchoolIconStore.save(this@WarehouseImportActivity, school.id, bmp) } } }
                ScheduleRepository.get(this@WarehouseImportActivity).reloadFromCache()
                CookieManager.getInstance().flush()
                Toast.makeText(this@WarehouseImportActivity, t.warehouseImported, Toast.LENGTH_LONG).show()
                finish()
            }.onFailure { fail(t.msgSaveFailed) }
        }
    }

    @Composable private fun Content() {
        val text = LocalStrings.current
        var choose by remember { mutableStateOf(false) }
        var license by remember { mutableStateOf(false) }
        Scaffold(topBar = { TopAppBar(title = { Text(school.name, maxLines = 1) }, navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, text.cancel) } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(text.warehouseInstructions, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { choose = true }, enabled = !busy) { Text(text.chooseAdapter) }
                    TextButton(onClick = { license = true }) { Text(text.warehouseSource) }
                }
                Text(adapters[adapterIndex].name + " · " + adapters[adapterIndex].maintainer, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                Text(page, Modifier.padding(horizontal = 16.dp), maxLines = 2, style = MaterialTheme.typography.labelSmall)
                AndroidView(factory = { browser() }, modifier = Modifier.weight(1f).fillMaxWidth())
                if (status.isNotBlank()) Text(status, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (web?.canGoBack() == true) web?.goBack() else web?.loadUrl(adapters[adapterIndex].url) }, enabled = !busy) { Text("←") }
                    if (busy) TextButton(onClick = { fail("") }) { Text(text.cancel) }
                    Button(onClick = { runScript() }, enabled = !busy) { Text(if (busy) text.warehouseBusy else text.warehouseRun) }
                }
            }
        }
        if (choose) AlertDialog(onDismissRequest = { choose = false }, title = { Text(text.chooseAdapter) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { adapters.forEachIndexed { i, adapter ->
                TextButton(onClick = { adapterIndex = i; choose = false; batch = null; status = ""; web?.loadUrl(adapter.url) }) { Text(adapter.name + " · " + adapter.category) }
            } }
        }, confirmButton = { TextButton(onClick = { choose = false }) { Text(text.cancel) } })
        dialog?.let { request -> BridgeDialog(request) }
        batch?.let { result -> Preview(result) }
        if (license) WarehouseLicenseDialog { license = false }
    }

    @Composable private fun Preview(result: WarehousePayload.Batch) {
        val text = LocalStrings.current
        var date by remember(result) { mutableStateOf(result.firstMonday?.toString().orEmpty()) }
        var previewTimes by remember(result) { mutableStateOf(result.times.ifEmpty { PeriodTimes.current() }) }
        var editTimes by remember(result) { mutableStateOf(false) }
        val maxPeriod = result.courses.maxOf { it.endPeriod }
        val validTimes = previewTimes.size >= maxPeriod && previewTimes.all { it.second.isAfter(it.first) } && previewTimes.zipWithNext().all { (a, b) -> !b.first.isBefore(a.second) }
        LaunchedEffect(result) {
            if (date.isBlank()) {
                val saved = SettingsStore.settingsFlow(this@WarehouseImportActivity).first()
                if (saved.schoolId == school.id && (saved.anchorEpochDay ?: 0) > 0) date = LocalDate.ofEpochDay(saved.anchorEpochDay!!).toString()
            }
        }
        val anchor = runCatching { LocalDate.parse(date.trim()) }.getOrNull()?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY }
        AlertDialog(onDismissRequest = { if (!busy) batch = null }, title = { Text(text.warehousePreview) }, text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(date, { date = it }, label = { Text(text.semesterStart) }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true, isError = anchor == null)
                Text("${result.courses.size} · ${result.totalWeeks}", Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { editTimes = true }) { Text(text.btnEditPeriodTimes) }
                if (!validTimes) Text(text.msgPeriodTimesInvalid, color = MaterialTheme.colorScheme.error)
                previewTimes.forEachIndexed { i, pair -> Text("${i + 1}  ${PeriodTimes.format(pair.first)} – ${PeriodTimes.format(pair.second)}", style = MaterialTheme.typography.bodySmall) }
                result.courses.forEach { c ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(c.title)
                    Text("${text.weekdayShort(c.dayOfWeek)} · ${c.startPeriod}–${c.endPeriod} · ${c.weeksRaw}", style = MaterialTheme.typography.bodySmall)
                    if (c.room.isNotBlank()) Text(c.room, style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(enabled = anchor != null && validTimes && !busy, onClick = { anchor?.let { save(result.copy(times = previewTimes), it) } }) { Text(text.confirmImport) } }, dismissButton = { TextButton(onClick = { batch = null }, enabled = !busy) { Text(text.cancel) } })
        if (editTimes) PeriodTimesDialog(restoreDefaults = PeriodTimes.defaults, onDismiss = { editTimes = false },
            onSave = { previewTimes = it; editTimes = false }, onInvalid = { Toast.makeText(this, text.msgPeriodTimesInvalid, Toast.LENGTH_SHORT).show() }, initialTimes = previewTimes)
    }

    @Composable private fun BridgeDialog(request: Request) {
        val text = LocalStrings.current
        var input by remember(request) { mutableStateOf(request.data.optString("value")) }
        var error by remember(request) { mutableStateOf("") }
        var validating by remember(request) { mutableStateOf(false) }
        val options = remember(request) { runCatching { JSONArray(request.data.optString("items")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()) }
        AlertDialog(onDismissRequest = { answer(request, if (request.action == "alert") false else null) }, title = { Text(request.data.optString("title")) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                when (request.action) {
                    "alert" -> Text(request.data.optString("content"))
                    "prompt" -> {
                        Text(request.data.optString("tip"))
                        OutlinedTextField(input, { input = it }, isError = error.isNotBlank(), supportingText = { if (error.isNotBlank()) Text(error) })
                    }
                    "selection" -> options.forEachIndexed { i, item -> TextButton(onClick = { answer(request, i) }) { Text(item) } }
                }
            }
        }, confirmButton = {
            if (request.action != "selection") TextButton(enabled = !validating, onClick = {
                if (request.action == "alert") answer(request, true) else {
                    val validator = request.data.optString("validator")
                    if (validator.isBlank()) answer(request, input) else if (validator.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) {
                        validating = true
                        web?.evaluateJavascript("(function(){try { return window.__kagendaSgValidate(${JSONObject.quote(validator)}, ${JSONObject.quote(input)}); } catch(e) { return String(e); }})()") { raw ->
                            validating = false
                            if (dialog === request && request.token == token && busy) {
                                if (raw == "false" || raw == "null") answer(request, input) else error = runCatching { org.json.JSONTokener(raw).nextValue().toString() }.getOrDefault(text.warehouseInvalid)
                            }
                        }
                    } else error = text.warehouseInvalid
                }
            }) { Text(request.data.optString("confirm").ifBlank { text.confirmImport }) }
        }, dismissButton = { TextButton(onClick = { answer(request, if (request.action == "alert") false else null) }) { Text(text.cancel) } })
    }

    override fun onDestroy() {
        timeout?.cancel(); busy = false; token = ""
        web?.apply { stopLoading(); removeJavascriptInterface("_kagendaWarehouse"); destroy() }; web = null
        super.onDestroy()
    }
}

@Composable
internal fun WarehouseLicenseDialog(onDismiss: () -> Unit) {
    val t = LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val license = remember { listOf("shiguang-warehouse-MIT.txt", "school-badges-Apache-2.0.txt", "pypinyin-MIT.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t.warehouseSource) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            Text(t.licenseAttribution)
            Text(SchoolCatalog.COMMIT, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { uri.openUri(SchoolCatalog.REPOSITORY) }) { Text("GitHub · ShiGuangSchedule") }
            TextButton(onClick = { uri.openUri("https://github.com/lovefc/china_school_badge") }) { Text("GitHub · lovefc") }
            Text(license, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } })
}
