@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.kstudio.agenda.ui.components.CollapsibleSectionCard
import com.kstudio.agenda.ui.components.SectionCard
import com.kstudio.agenda.ui.components.TrailingChevron
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
    // 时间线范围选择器：""=无；"start"/"end"
    var timelinePick by remember { mutableStateOf("") }
    var aiKeyInput by rememberSaveable { mutableStateOf("") }
    var aiModel by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(settings.aiModel) {
        if (aiModel.isBlank()) aiModel = settings.aiModel
    }

    LaunchedEffect(settings.studentId) {
        if (studentId.isBlank()) studentId = settings.studentId
    }

    // ---------------- 分类与卡片展开状态 ----------------
    // -1 = 分类菜单（设置页首屏）；0 学校&账号 / 1 功能&权限 / 2 用户自定义 / 3 数据&图片 / 4 语言 / 5 关于
    // 首屏只列分类入口，点进某一类后才显示该类目的具体选项（二级页可返回）
    var settingsTab by rememberSaveable { mutableStateOf(-1) }
    var expPermissions by rememberSaveable { mutableStateOf(false) }
    var expReminder by rememberSaveable { mutableStateOf(true) }
    var expAi by rememberSaveable { mutableStateOf(true) }
    var expOverlay by rememberSaveable { mutableStateOf(true) }
    var expStatus by rememberSaveable { mutableStateOf(true) }
    var expWidget by rememberSaveable { mutableStateOf(true) }
    var expTimeline by rememberSaveable { mutableStateOf(true) }
    var expWidgetRefresh by rememberSaveable { mutableStateOf(true) }
    var expData by rememberSaveable { mutableStateOf(true) }
    var expLang by rememberSaveable { mutableStateOf(true) }
    var expSchool by rememberSaveable { mutableStateOf(true) }
    // 关于：默认折叠（折叠时右侧显示 K日程 + 版本号）
    var expAbout by rememberSaveable { mutableStateOf(false) }
    // 账号：未登录默认展开、已登录默认折叠；用户手动切过之后不再跟随登录状态
    val loggedIn = settings.hasPassword || syncState is SyncUi.Success
    var expAccount by rememberSaveable { mutableStateOf(false) }
    var accountToggled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loggedIn) { if (!accountToggled) expAccount = !loggedIn }
    // 滚动状态提升到「开发者工具」早退之前：进出开发者工具 / 日志页后不会跳回顶部
    val listState = rememberLazyListState()
    LaunchedEffect(settingsTab) {
        runCatching { listState.scrollToItem(0) }
    }

    // 分类标题/说明（分类菜单与二级页顶栏共用）
    val categoryTitles = listOf(
        t.settingsTabAccount,
        t.settingsTabFeature,
        t.settingsTabCustom,
        t.secData,
        t.secLang,
        t.secAbout,
    )
    val categorySubs = listOf(
        t.settingsTabAccountSub,
        t.settingsTabFeatureSub,
        t.settingsTabCustomSub,
        t.secDataSub,
        t.secLangSub,
        t.aboutSub,
    )

    // 存储权限（仅 Android 8.0–9 导出图片需要）当前是否已授予
    val storageNeeded = Build.VERSION.SDK_INT < 29
    var storageAllowed by remember {
        mutableStateOf(
            !storageNeeded || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    // 未授予的权限数量在下方全部权限状态声明之后计算（折叠时显示在权限卡片右侧）

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

    // 精确闹钟 / 忽略电池优化：跳系统页后回来刷新状态（用启动器才能在返回时回调）
    val exactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { exactAllowed = ReminderScheduler.canScheduleExact(context) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = ReminderScheduler.isIgnoringBatteryOptimizations(context) }
    // 存储权限：申请按钮（权限状态与统计见上方）
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { storageAllowed = it }

    // 未授予的权限数量（折叠时显示在权限卡片右侧）
    val missingPermCount = listOf(!notifAllowed, !overlayAllowed, !exactAllowed, !batteryExempt)
        .count { it } + if (storageNeeded && !storageAllowed) 1 else 0

    if (showDevTools) {
        DeveloperToolsScreen(vm, onClose = { showDevTools = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        // 首屏只显示分类入口；进入某个分类后顶部显示「返回 + 分类名」
        if (settingsTab >= 0) {
            SettingsSubHeader(
                title = categoryTitles.getOrElse(settingsTab) { "" },
                onBack = { settingsTab = -1 },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        // ---------------------------------------------------------- 设置首屏：分类菜单
        if (settingsTab < 0) item {
            SettingsCategoryMenu(
                titles = categoryTitles,
                subs = categorySubs,
                onOpen = { settingsTab = it },
            )
        }
        // ---------------------------------------------------------- 【选项卡 0】学校&账号：学校（可切换）
        if (settingsTab == 0) item {
            CollapsibleSectionCard(
                t.secSchool,
                t.secSchoolSub,
                expanded = expSchool,
                onToggle = { expSchool = !expSchool },
            ) {
                val school = Schools.of(settings.schoolId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSchoolPicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SchoolLogo(school, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = school.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TrailingChevron()
                }
            }
        }
        // ---------------------------------------------------------- 【选项卡 0】学校&账号：账号
        // 未登录时默认展开（直接看到登录表单），已登录默认折叠（右侧显示学号）
        if (settingsTab == 0) item {
            CollapsibleSectionCard(
                t.secAccount,
                t.secAccountSubOf(settings.schoolId, Schools.of(settings.schoolId).name),
                expanded = expAccount,
                onToggle = {
                    accountToggled = true
                    expAccount = !expAccount
                },
                trailing = settings.studentId.takeIf { it.isNotBlank() },
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
                        // 学号为空时不清空密码框，方便用户补全学号后直接重试
                        if (studentId.isNotBlank()) password = ""
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
                        // 有已保存账密，或有有效网页会话时都可退出登录（否则拿了 Cookie 会话的用户
                        // 只能走“重置应用”才能清会话）
                        enabled = settings.hasPassword || syncState is SyncUi.Success,
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

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：权限设置（默认折叠）
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secPermissions,
                t.secPermissionsSub,
                expanded = expPermissions,
                onToggle = { expPermissions = !expPermissions },
                trailing = if (missingPermCount == 0) t.permGranted
                else t.permMissingCount(missingPermCount),
            ) {
                PermissionRow(t.permNetworkName, t.permNetworkWhy, granted = true)
                PermissionRow(
                    name = t.permNotifName,
                    why = t.permNotifWhy,
                    granted = notifAllowed,
                    actionLabel = t.permRequest,
                    onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
                PermissionRow(
                    name = t.permOverlayName,
                    why = t.permOverlayWhy,
                    granted = overlayAllowed,
                    actionLabel = t.gotoGrant,
                    onAction = launchOverlayPermission,
                )
                PermissionRow(
                    name = t.permExactName,
                    why = t.permExactWhy,
                    granted = exactAllowed,
                    actionLabel = t.gotoGrant,
                    onAction = { exactLauncher.launch(exactAlarmIntent(context)) },
                )
                PermissionRow(
                    name = t.permBatteryName,
                    why = t.permBatteryWhy,
                    granted = batteryExempt,
                    actionLabel = t.gotoExempt,
                    onAction = {
                        batteryLauncher.launch(ReminderScheduler.batteryExemptionIntent(context))
                    },
                )
                if (storageNeeded) {
                    PermissionRow(
                        name = t.permStorageName,
                        why = t.permStorageWhy,
                        granted = storageAllowed,
                        actionLabel = t.permRequest,
                        onAction = {
                            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        },
                    )
                }
            }
        }

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：课前提醒
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secReminder,
                t.secReminderSub,
                expanded = expReminder,
                onToggle = { expReminder = !expReminder },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.reminderEnable, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { want ->
                            vm.setReminderEnabled(want)
                            // 开启提醒时才申请通知权限（安装后不主动要权限）
                            if (want && !Notifier.hasPermission(context)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
                // 提醒关闭时（默认状态）不再展示提前时间与授权状态细节，
                // 避免“功能本来没开，却在提示未授权/未解除”的困扰
                if (settings.reminderEnabled) {
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
                                exactLauncher.launch(exactAlarmIntent(context))
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
                                batteryLauncher.launch(
                                    ReminderScheduler.batteryExemptionIntent(context)
                                )
                            }) { Text(t.gotoExempt) }
                        }
                    }
                }
                // 通知权限：课前提醒或常驻通知任一开启时才提示（默认全关时保持安静）
                if (settings.reminderEnabled || settings.statusNotifEnabled) {
                    Spacer(Modifier.height(4.dp))
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
        }

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：时间线显示范围
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secTimelineRange,
                t.secTimelineRangeSub,
                expanded = expTimeline,
                onToggle = { expTimeline = !expTimeline },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.fieldStartTime, modifier = Modifier.weight(1f))
                    TextButton(onClick = { timelinePick = "start" }) {
                        Text("%02d:%02d".format(settings.timelineStartMinutes / 60, settings.timelineStartMinutes % 60))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.fieldEndTime, modifier = Modifier.weight(1f))
                    TextButton(onClick = { timelinePick = "end" }) {
                        // 结束时间 ≤ 开始时间时显示为“次日 HH:mm”（跨天）
                        val crossDay = settings.timelineEndMinutes <= settings.timelineStartMinutes
                        Text(
                            (if (crossDay) t.labelNextDay + " " else "") +
                                "%02d:%02d".format(settings.timelineEndMinutes / 60, settings.timelineEndMinutes % 60)
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：小组件刷新频率
        // 默认（不自定义）已比旧版更快：临近 1 分钟 / 3 小时内 5 分钟 / 更远或无日程 60 分钟
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secWidgetRefresh,
                t.secWidgetRefreshSub,
                expanded = expWidgetRefresh,
                onToggle = { expWidgetRefresh = !expWidgetRefresh },
                trailing = if (settings.widgetRefreshCustom) t.widgetRefreshCustomOn else t.widgetRefreshDefaultTag,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.widgetRefreshCustom, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.widgetRefreshCustom,
                        onCheckedChange = { vm.setWidgetRefreshCustom(it) },
                    )
                }
                if (settings.widgetRefreshCustom) {
                    WidgetRefreshTierRow(
                        label = t.widgetRefreshNear,
                        tier = 0,
                        current = settings.widgetRefreshNearMinutes,
                        options = listOf(1, 2, 5, 10),
                        onPick = vm::setWidgetRefreshMinutes,
                    )
                    WidgetRefreshTierRow(
                        label = t.widgetRefreshSoon,
                        tier = 1,
                        current = settings.widgetRefreshSoonMinutes,
                        options = listOf(2, 5, 10, 15),
                        onPick = vm::setWidgetRefreshMinutes,
                    )
                    WidgetRefreshTierRow(
                        label = t.widgetRefreshFar,
                        tier = 2,
                        current = settings.widgetRefreshFarMinutes,
                        options = listOf(15, 30, 60, 120),
                        onPick = vm::setWidgetRefreshMinutes,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = t.widgetRefreshDefaultNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：AI 识别（DeepSeek）
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secAi,
                t.secAiSub,
                expanded = expAi,
                onToggle = { expAi = !expAi },
            ) {
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

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：悬浮球
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secOverlay,
                t.secOverlaySub,
                expanded = expOverlay,
                onToggle = { expOverlay = !expOverlay },
            ) {
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

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：常驻通知
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secStatus,
                t.secStatusSub,
                expanded = expStatus,
                onToggle = { expStatus = !expStatus },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.statusEnable, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.statusNotifEnabled,
                        onCheckedChange = { want ->
                            vm.setStatusNotifConfig(want, settings.statusNotifSources)
                            // 开启常驻通知时才申请通知权限
                            if (want && !Notifier.hasPermission(context)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
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
                // AI 快速添加入口（文本框样式）：可单独关闭，关闭后该常驻条目会被移除
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.statusAiEntry,
                        onCheckedChange = { vm.setStatusAiEntry(it) },
                    )
                    Text(t.statusSrcAi, modifier = Modifier.weight(1f))
                }
                Text(
                    text = t.statusHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 【选项卡 1】功能&权限：小组件
        if (settingsTab == 1) item {
            CollapsibleSectionCard(
                t.secWidget,
                t.secWidgetSub,
                expanded = expWidget,
                onToggle = { expWidget = !expWidget },
            ) {
                Text(
                    text = t.widgetPinHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "2×1" to com.kstudio.agenda.widget.NextClassWidget2x1::class.java,
                        "2×2" to com.kstudio.agenda.widget.NextClassWidget2x2::class.java,
                        "4×1" to com.kstudio.agenda.widget.NextClassWidget4x1::class.java,
                        "4×2" to com.kstudio.agenda.widget.NextClassWidget4x2::class.java,
                    ).forEach { (label, cls) ->
                        OutlinedButton(onClick = {
                            val awm = android.appwidget.AppWidgetManager.getInstance(context)
                            if (awm.isRequestPinAppWidgetSupported) {
                                // 向桌面请求钉住对应尺寸的小组件（系统会弹确认框）
                                awm.requestPinAppWidget(android.content.ComponentName(context, cls), null, null)
                            } else {
                                vm.message(t.widgetPinUnsupported)
                            }
                        }) { Text(label) }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 【选项卡 3】数据与图片
        if (settingsTab == 3) item {
            CollapsibleSectionCard(
                t.secData,
                t.secDataSub,
                expanded = expData,
                onToggle = { expData = !expData },
            ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.saveMonthImage(selectedDate.withDayOfMonth(1)) }) {
                        Text(t.btnSaveMonthImg)
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

        // ---------------------------------------------------------- 【选项卡 4】语言（中/法/英，默认跟随系统）
        if (settingsTab == 4) item {
            CollapsibleSectionCard(
                t.secLang,
                null,
                expanded = expLang,
                onToggle = { expLang = !expLang },
            ) {
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

        // ---------------------------------------------------------- 【选项卡 5】关于（默认折叠，右侧显示 K日程 + 版本号）
        if (settingsTab == 5) item {
            CollapsibleSectionCard(
                t.secAbout,
                t.aboutSub,
                expanded = expAbout,
                onToggle = { expAbout = !expAbout },
                trailing = t.aboutSub,
            ) {
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
        // ---------------------------------------------------------- 【选项卡 5】关于：开发者工具入口（整行可点，行尾尖角符）
        if (settingsTab == 5) item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showDevTools = true },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = t.secDev,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = t.secDevSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TrailingChevron()
                }
            }
        }
        }
    }

    // 时间线显示范围：开始/结束时间选择（仅精确时间滚轮）
    if (timelinePick.isNotEmpty()) {
        val isStart = timelinePick == "start"
        val cur = if (isStart) settings.timelineStartMinutes else settings.timelineEndMinutes
        TimeWheelDialog(
            initial = "%02d:%02d".format(cur / 60, cur % 60),
            allowFuzzy = false,
            onDismiss = { timelinePick = "" },
            onConfirm = { v ->
                val hh = v.substringBefore(":").toIntOrNull()
                val mm = v.substringAfter(":", "").toIntOrNull()
                if (hh != null && mm != null) {
                    val minutes = (hh * 60 + mm).coerceIn(0, 1439)
                    if (isStart) vm.setTimelineStart(minutes) else vm.setTimelineEnd(minutes)
                }
                timelinePick = ""
            },
        )
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

/** 打开系统的“闹钟和提醒”授权页（精确闹钟权限）用的 Intent */
private fun exactAlarmIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    )

/**
 * 设置首屏：分类入口（点进某一类后才显示该类目的具体选项）。
 * 把原来一排横向选项卡改成纵向列表，首屏不再一次堆满具体开关。
 */
@Composable
private fun SettingsCategoryMenu(
    titles: List<String>,
    subs: List<String>,
    onOpen: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        titles.forEachIndexed { index, title ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpen(index) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val sub = subs.getOrNull(index).orEmpty()
                        if (sub.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TrailingChevron()
                }
            }
        }
    }
}

/** 二级页顶栏：返回箭头 + 分类名（返回分类菜单） */
@Composable
private fun SettingsSubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 小组件刷新频率的一档：标题 + 可选分钟数（[onPick] 传入 tier 与分钟数） */
@Composable
private fun WidgetRefreshTierRow(
    label: String,
    tier: Int,
    current: Int,
    options: List<Int>,
    onPick: (Int, Int) -> Unit,
) {
    val t = LocalStrings.current
    Spacer(Modifier.height(10.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { minutes ->
            ChoiceChip(
                label = t.minutes(minutes),
                selected = current == minutes,
                onClick = { onPick(tier, minutes) },
            )
        }
    }
}

/** 权限行：权限名 + 当前状态 + 用途说明 +（未授予时）申请/授权按钮 */
@Composable
private fun PermissionRow(
    name: String,
    why: String,
    granted: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (granted) t.permGranted else t.permNotGranted,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted && actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
