@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.notif.Notifier
import com.kstudio.agenda.notif.ReminderScheduler
import com.kstudio.agenda.ui.components.ChoiceChip
import com.kstudio.agenda.ui.components.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 【已隐藏保留】网页登录入口开关
 *
 * 按需求暂时隐藏「网页登录」入口，界面仅展示账密登录。
 * 相关代码（本文件按钮、MainScreen 的启动器、WebLoginActivity、AndroidManifest 注册等）
 * 全部保留在项目中，未删除；需要恢复入口时把此常量改为 true 即可。
 */
private const val SHOW_WEB_LOGIN_ENTRY = false

@Composable
fun SettingsScreen(vm: AppViewModel, onWebLogin: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val context = LocalContext.current
    val t = LocalStrings.current

    var studentId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var exactAllowed by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
    var notifAllowed by remember { mutableStateOf(Notifier.hasPermission(context)) }
    var batteryExempt by remember {
        mutableStateOf(ReminderScheduler.isIgnoringBatteryOptimizations(context))
    }
    // 开发者工具：独立页面打开
    var showDevTools by remember { mutableStateOf(false) }
    var showClearCredConfirm by remember { mutableStateOf(false) }
    var showSchoolPicker by remember { mutableStateOf(false) }
    var showAdapterDialog by remember { mutableStateOf(false) }
    var aiKeyInput by rememberSaveable { mutableStateOf("") }
    var aiModel by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(settings.aiModel) {
        if (aiModel.isBlank()) aiModel = settings.aiModel
    }

    LaunchedEffect(settings.studentId) {
        if (studentId.isBlank()) studentId = settings.studentId
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifAllowed = granted }

    // 悬浮球：悬浮窗（显示在其他应用上层）权限
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayPending by remember { mutableStateOf(false) }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (overlayAllowed && overlayPending) {
            vm.setFloatingBall(true)
        } else if (!overlayAllowed) {
            vm.message(t.overlayNeedPerm)
        }
        overlayPending = false
    }
    val launchOverlayPermission = {
        runCatching {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }
        Unit
    }

    if (showDevTools) {
        DeveloperToolsScreen(vm, onClose = { showDevTools = false })
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---------------------------------------------------------- 学校（可切换；北航已完整适配）
        item {
            SectionCard(t.secSchool, t.secSchoolSub) {
                val school = Schools.of(settings.schoolId)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SchoolLogo(school, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(school.name, style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(onClick = { showSchoolPicker = true }) { Text(t.schoolChange) }
                }
            }
        }

        // ---------------------------------------------------------- 账号
        item {
            SectionCard(
                t.secAccount,
                t.secAccountSubOf(settings.schoolId, Schools.of(settings.schoolId).name),
            ) {
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = { Text(t.labelStudentId) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            if (settings.hasPassword) t.labelPasswordKeep
                            else t.labelPassword
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        vm.saveAccountAndSync(studentId, password.takeIf { it.isNotBlank() })
                        password = ""
                    }) {
                        Text(t.btnSaveAndSync)
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    // 【已隐藏保留】网页登录入口（当前隐藏，仅保留账密登录；代码未删除）
                    if (SHOW_WEB_LOGIN_ENTRY) {
                        OutlinedButton(onClick = onWebLogin) {
                            Text("网页登录")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { vm.logout() },
                        enabled = settings.hasPassword,
                    ) { Text(t.btnLogout) }
                    TextButton(
                        onClick = { showClearCredConfirm = true },
                        enabled = settings.hasPassword,
                    ) { Text(t.btnClearCred) }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = when {
                        settings.hasPassword -> t.statusSaved(settings.studentId)
                        // 【已隐藏保留】原表述为“已通过网页登录（未保存密码）”；入口隐藏后改为中性表述
                        syncState is SyncUi.Success -> t.statusSession
                        else -> t.statusNone
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.hasPassword || syncState is SyncUi.Success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = t.lastSyncAt(
                        if (settings.lastSyncAtMillis > 0L) formatTime(settings.lastSyncAtMillis)
                        else t.neverSynced
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 【已隐藏保留】原文案末尾含“请点「网页登录」”引导；入口隐藏后不再引导
                Text(
                    text = t.accountFootnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 提醒
        item {
            SectionCard(t.secReminder, t.secReminderSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.reminderEnable, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { vm.setReminderEnabled(it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.leadLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 20, 30, 45, 60).forEach { minutes ->
                        ChoiceChip(
                            label = t.minutes(minutes),
                            selected = settings.leadMinutes == minutes,
                            onClick = { vm.setLeadMinutes(minutes) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (exactAllowed) t.exactOk else t.exactNo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!exactAllowed) {
                        TextButton(onClick = {
                            openExactAlarmSettings(context)
                            exactAllowed = ReminderScheduler.canScheduleExact(context)
                        }) { Text(t.gotoGrant) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (batteryExempt) t.batteryOk else t.batteryNo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!batteryExempt) {
                        TextButton(onClick = {
                            ReminderScheduler.requestIgnoreBatteryOptimizations(context)
                        }) { Text(t.gotoExempt) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (notifAllowed) t.notifOk else t.notifNo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!notifAllowed) {
                        TextButton(onClick = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text(t.enableNotif) }
                    }
                }
            }
        }

        // ---------------------------------------------------------- AI 识别（DeepSeek）
        item {
            SectionCard(t.secAi, t.secAiSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.useDevAiKey,
                        onCheckedChange = { vm.setUseDevAiKey(it) },
                    )
                    Text(t.aiUseDevKey)
                }
                if (settings.useDevAiKey) {
                    Text(
                        text = t.aiDevKeyInUse,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = aiKeyInput,
                        onValueChange = { aiKeyInput = it },
                        label = { Text(t.aiKeyLabel) },
                        placeholder = { if (settings.aiKeySet) Text(t.aiKeyKeepHint) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = if (settings.useDevAiKey) SettingsStore.DEV_AI_MODEL else aiModel,
                    onValueChange = { if (!settings.useDevAiKey) aiModel = it },
                    label = { Text(t.aiModelLabel) },
                    singleLine = true,
                    enabled = !settings.useDevAiKey,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (settings.useDevAiKey) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = t.aiDevModelFixed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!settings.useDevAiKey) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.setAiModel(aiModel)
                            if (aiKeyInput.isNotBlank()) {
                                vm.setAiKey(aiKeyInput.trim())
                                aiKeyInput = ""
                            } else {
                                vm.message(t.aiSavedToast)
                            }
                        }) { Text(t.aiSave) }
                        OutlinedButton(
                            onClick = { vm.setAiKey(null) },
                            enabled = settings.aiKeySet,
                        ) { Text(t.aiClear) }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 悬浮球（在其他应用上层显示，无需打开 App）
        item {
            SectionCard(t.secOverlay, t.secOverlaySub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.overlayEnable, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.floatingBall,
                        onCheckedChange = { want ->
                            if (want) {
                                if (overlayAllowed) {
                                    vm.setFloatingBall(true)
                                } else {
                                    overlayPending = true
                                    launchOverlayPermission()
                                }
                            } else {
                                vm.setFloatingBall(false)
                            }
                        },
                    )
                }
                if (!overlayAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t.overlayNeedPerm,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = {
                            overlayPending = false
                            launchOverlayPermission()
                        }) { Text(t.overlayGrant) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t.overlayHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 常驻通知（锁屏可见、内容可选）
        item {
            SectionCard(t.secStatus, t.secStatusSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.statusEnable, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.statusNotifEnabled,
                        onCheckedChange = { vm.setStatusNotifConfig(it, settings.statusNotifSources) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                val sources = settings.statusNotifSources.split(',').map { it.trim() }.toSet()
                listOf(
                    "course" to t.statusSrcCourse,
                    "plan" to t.statusSrcPlan,
                    "agenda" to t.statusSrcAgenda,
                ).forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in sources,
                            onCheckedChange = { checked ->
                                val newSet = if (checked) sources + key else sources - key
                                vm.setStatusNotifConfig(
                                    settings.statusNotifEnabled,
                                    newSet.joinToString(","),
                                )
                            },
                        )
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
                Text(
                    text = t.statusHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 数据
        item {
            SectionCard(t.secData, t.secDataSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.autoRefresh, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.autoRefresh,
                        onCheckedChange = { vm.setAutoRefresh(it) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.syncNow() }) { Text(t.btnSyncNow) }
                    OutlinedButton(onClick = { vm.clearCache() }) { Text(t.btnClearCache) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.saveDayImage(selectedDate) }) {
                        Text(t.btnSaveDayImg)
                    }
                    OutlinedButton(onClick = { vm.saveWeekImage() }) {
                        Text(t.btnSaveWeekImg)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.imgDirNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 语言（中/法/英，默认跟随系统）
        item {
            SectionCard(t.secLang, null) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(
                        label = t.langSystem,
                        selected = settings.appLanguage.isEmpty(),
                        onClick = { vm.setAppLanguage("") },
                    )
                    ChoiceChip(
                        label = "中文",
                        selected = settings.appLanguage == "zh",
                        onClick = { vm.setAppLanguage("zh") },
                    )
                    ChoiceChip(
                        label = "Français",
                        selected = settings.appLanguage == "fr",
                        onClick = { vm.setAppLanguage("fr") },
                    )
                    ChoiceChip(
                        label = "English",
                        selected = settings.appLanguage == "en",
                        onClick = { vm.setAppLanguage("en") },
                    )
                }
            }
        }

        // ---------------------------------------------------------- 开发者工具（独立页面）
        item {
            SectionCard(t.secDev, t.secDevSub) {
                OutlinedButton(onClick = { showDevTools = true }) {
                    Text(t.openDevTools)
                }
            }
        }

        // ---------------------------------------------------------- 关于
        item {
            SectionCard(t.secAbout, t.aboutSub) {
                Text(
                    text = t.aboutBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.aboutTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.aboutPeriods,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showClearCredConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCredConfirm = false },
            title = { Text(t.askClearCredTitle) },
            text = { Text(t.askClearCredBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearCredConfirm = false
                    vm.clearSavedCredentials()
                }) { Text(t.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCredConfirm = false }) { Text(t.cancel) }
            },
        )
    }

    if (showSchoolPicker) {
        AlertDialog(
            onDismissRequest = { showSchoolPicker = false },
            title = { Text(t.secSchool) },
            text = {
                Column {
                    Schools.all().forEach { school ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setSchool(school.id)
                                    showSchoolPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SchoolLogo(school, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(school.name, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (settings.schoolId == school.id) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            showSchoolPicker = false
                            showAdapterDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t.schoolAdd) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSchoolPicker = false }) { Text(t.cancel) }
            },
        )
    }

    if (showAdapterDialog) {
        SchoolAdapterDialog(vm) { showAdapterDialog = false }
    }
}

/** 「添加学校」对话框：粘贴/生成适配代码（KagendaSchoolAdapter/1），校验后注册 */
@Composable
private fun SchoolAdapterDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var site by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }

    // 生成阶段提示：等待时间实时计数（AI 生成通常较慢，让用户知道还在进行）
    LaunchedEffect(busy) {
        if (!busy) {
            elapsed = 0
            return@LaunchedEffect
        }
        val startAt = System.currentTimeMillis()
        while (true) {
            elapsed = ((System.currentTimeMillis() - startAt) / 1000).toInt()
            delay(500)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.schoolAdd) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t.adapterName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text(t.adapterSite) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text(t.adapterExtraLabel) },
                    placeholder = {
                        Text(
                            text = t.adapterExtraHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        if (name.isBlank()) {
                            errorMsg = true
                            msg = t.adapterNeedName
                            return@OutlinedButton
                        }
                        busy = true
                        errorMsg = false
                        msg = t.adapterStageRequest
                        scope.launch {
                            try {
                                val key = SettingsStore.effectiveAiKey(context)
                                if (key.isNullOrBlank()) {
                                    errorMsg = true
                                    msg = t.aiNeedKey
                                    return@launch
                                }
                                val model = SettingsStore.effectiveAiModel(context)
                                val reply = AiClient.chat(
                                    apiKey = key,
                                    model = model,
                                    systemPrompt = AiSkills.adapterAuthorSystemPrompt,
                                    userPrompt = AiSkills.adapterUserPrompt(name, site, extra),
                                )
                                msg = t.adapterStageParse
                                val json = AiSkills.extractFirstJson(reply)
                                if (json == null) {
                                    errorMsg = true
                                    msg = t.parseFail
                                } else {
                                    code = json
                                    errorMsg = false
                                    msg = t.adapterStageDone
                                }
                            } catch (e: Throwable) {
                                errorMsg = true
                                msg = (e.message ?: t.parseFail).take(80)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) { Text(t.adapterGen) }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(t.adapterCodeLabel) },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(Schools.template()))
                    vm.message(t.adapterCopied)
                }) { Text(t.adapterTemplate) }
                if (msg.isNotBlank()) {
                    Text(
                        text = if (busy && elapsed > 0) msg + t.adapterWaiting(elapsed) else msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (errorMsg) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code.isBlank()) {
                        vm.message(t.adapterInvalid("JSON"))
                        return@TextButton
                    }
                    vm.addCustomSchool(code, name, site)
                    onDismiss()
                },
                enabled = !busy,
            ) { Text(t.adapterSave) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        },
    )
}

/** 学校图标：北航用内置 logo，其余用名称首字圆标 */
@Composable
private fun SchoolLogo(school: School, size: Dp) {
    if (school.id == "buaa") {
        Image(
            painter = painterResource(R.drawable.school_buaa),
            contentDescription = school.name,
            modifier = Modifier.size(size),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = school.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

/** 打开系统的“闹钟和提醒”授权页（精确闹钟权限） */
private fun openExactAlarmSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }
}
