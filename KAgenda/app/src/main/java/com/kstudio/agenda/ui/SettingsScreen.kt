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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.AppUpdater
import com.kstudio.agenda.data.InstallStart
import com.kstudio.agenda.data.SchoolFlows
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.data.UpdateUi
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.School
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.notif.Notifier
import com.kstudio.agenda.notif.ReminderScheduler
import com.kstudio.agenda.ui.components.ChoiceChip
import com.kstudio.agenda.ui.components.CollapsibleSectionCard
import com.kstudio.agenda.ui.components.SectionCard
import com.kstudio.agenda.ui.components.TrailingChevron
import com.kstudio.agenda.ui.components.cardGlassBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/**
 * 【已隐藏保留】网页登录入口开关
 *
 * 按需求暂时隐藏「网页登录」入口，界面仅展示账密登录。
 * 相关代码（本文件按钮、MainScreen 的启动器、WebLoginActivity、AndroidManifest 注册等）
 * 全部保留在项目中，未删除；需要恢复入口时把此常量改为 true 即可。
 *
 * 例外：对「必须手动登录」的学校（数据字段 `School.manualLogin`，如门户带滑块验证码 +
 * 短信二次认证的学校，脚本无法代填），此入口**始终显示**——否则用户没有任何途径完成登录。
 */
private const val SHOW_WEB_LOGIN_ENTRY = false

/** 该学校是否必须由用户手动完成登录（此时强制显示「网页登录」入口） */
private fun requiresManualLogin(schoolId: String?): Boolean =
    com.kstudio.agenda.model.Schools.of(schoolId).manualLogin

/** 更新包大小文案（拿不到大小时显示「大小未知」） */
private fun sizeLabel(bytes: Long, unknown: String): String =
    com.kstudio.agenda.data.AppUpdater.formatBytes(bytes).ifBlank { unknown }

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onWebLogin: () -> Unit,
    onOpenDocImport: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val syncState by vm.syncState.collectAsState()
    // 课表里的全部课程（用于“节数配置是否够用”的提醒）
    val semesterCourses = vm.semester.collectAsState().value?.weeks?.values?.flatten().orEmpty()
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
    // 课程时间自定义对话框
    var showPeriodTimes by remember { mutableStateOf(false) }
    // 应用内更新：状态 + 安装权限弹窗
    val updateState by AppUpdater.state.collectAsState()
    var showInstallPermDialog by remember { mutableStateOf(false) }
    // 课程修改：卡片展开与两个入口的确认弹窗
    var expCourseEdits by rememberSaveable { mutableStateOf(true) }
    var showRestoreEditsConfirm by remember { mutableStateOf(false) }
    var showSyncClearEditsConfirm by remember { mutableStateOf(false) }
    val courseEditCount by vm.courseEditCount.collectAsState()
    var aiKeyInput by rememberSaveable { mutableStateOf("") }
    var aiModel by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(settings.aiModel) {
        if (aiModel.isBlank()) aiModel = settings.aiModel
    }

    // 进入「关于」页时静默检查一次更新（内部按 6 小时节流；手动点右上角刷新图标则强制请求）

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
    var expPeriodTimes by rememberSaveable { mutableStateOf(true) }
    var expUiStyle by rememberSaveable { mutableStateOf(true) }
    var expData by rememberSaveable { mutableStateOf(true) }
    var expHoliday by rememberSaveable { mutableStateOf(true) }
    var expBackup by rememberSaveable { mutableStateOf(true) }
    var expDocs by rememberSaveable { mutableStateOf(true) }
    var expLang by rememberSaveable { mutableStateOf(true) }
    var expSchool by rememberSaveable { mutableStateOf(true) }
    // 关于：默认折叠（折叠时右侧显示 K日程 + 版本号）
    var expAbout by rememberSaveable { mutableStateOf(false) }
    var expFeedback by rememberSaveable { mutableStateOf(true) }
    // 用户反馈表单
    var feedbackTopic by rememberSaveable { mutableStateOf(0) }
    var feedbackText by rememberSaveable { mutableStateOf("") }
    var feedbackContact by rememberSaveable { mutableStateOf("") }
    // 账号：未登录默认展开、已登录默认折叠；用户手动切过之后不再跟随登录状态
    val loggedIn = settings.hasPassword || syncState is SyncUi.Success
    var expAccount by rememberSaveable { mutableStateOf(false) }
    var expProfile by rememberSaveable { mutableStateOf(false) }
    // 身份预设（学院/专业/年级/班级）：初值取设置；保存后 DataStore 回流刷新
    var profCollege by remember(settings.profileCollege) { mutableStateOf(settings.profileCollege) }
    var profMajor by remember(settings.profileMajor) { mutableStateOf(settings.profileMajor) }
    var profGrade by remember(settings.profileGrade) { mutableStateOf(settings.profileGrade) }
    var profClazz by remember(settings.profileClazz) { mutableStateOf(settings.profileClazz) }
    var accountToggled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loggedIn) { if (!accountToggled) expAccount = !loggedIn }
    // 滚动状态提升到「开发者工具」早退之前：进出开发者工具 / 日志页后不会跳回顶部
    val listState = rememberLazyListState()
    LaunchedEffect(settingsTab) {
        runCatching { listState.scrollToItem(0) }
        // 进入「关于」页时静默检查一次更新（内部按 6 小时节流；手动点刷新图标则强制请求）
        if (settingsTab == 5) AppUpdater.check(context, auto = true)
    }

    // 分类标题/说明（分类菜单与二级页顶栏共用）
    val categoryTitles = listOf(
        t.settingsTabAccount,
        t.settingsTabFeature,
        t.settingsTabCustom,
        t.secData,
        t.secLang,
        t.settingsTabAboutFeedback,
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

    // 安装未知应用（应用内更新安装新版 APK 需要）：跳系统页后回来刷新状态
    var installAllowed by remember { mutableStateOf(AppUpdater.canInstallPackages(context)) }
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { installAllowed = AppUpdater.canInstallPackages(context) }

    // 数据备份与导入（系统文件选择器，不需要任何权限；另有「下载/KAgenda」默认位置）
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var showExportChoice by remember { mutableStateOf(false) }
    var importChoices by remember { mutableStateOf<List<com.kstudio.agenda.data.BackupStore.Entry>>(emptyList()) }
    var showImportChoice by remember { mutableStateOf(false) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    // 未授予的权限数量（折叠时显示在权限卡片右侧）
    val missingPermCount = listOf(
        !notifAllowed,
        !overlayAllowed,
        !exactAllowed,
        !batteryExempt,
        !installAllowed,
    ).count { it } + if (storageNeeded && !storageAllowed) 1 else 0

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
            // 二级页顶栏与内容之间的分隔线（提高层级可辨认度）
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                thickness = 1.dp,
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
        // ---------------------------------------------------------- 【选项卡 0】学校&账号：身份预设
        // 用途：很多通知会同时列出多个班级/多个时间分支，填了身份后 AI 助手只挑与本人相符的那一条
        if (settingsTab == 0) item {
            CollapsibleSectionCard(
                t.secProfile,
                t.secProfileSub,
                expanded = expProfile,
                onToggle = { expProfile = !expProfile },
                trailing = settings.profileText.takeIf { it.isNotBlank() },
            ) {
                OutlinedTextField(
                    value = profCollege,
                    onValueChange = { profCollege = it },
                    label = { Text(t.labelCollege) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = profMajor,
                    onValueChange = { profMajor = it },
                    label = { Text(t.labelMajor) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = profGrade,
                    onValueChange = { profGrade = it },
                    label = { Text(t.labelGrade) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = profClazz,
                    onValueChange = { profClazz = it },
                    label = { Text(t.labelClazz) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.setProfile(profCollege, profMajor, profGrade, profClazz) }) {
                    Text(t.btnSaveProfile)
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
                // 学校专属开关（由学校流程插件提供描述，如「通过 WebVPN」）。
                // 本处不含任何学校名字：新增学校只要在插件里声明开关就会自动出现。
                SchoolFlows.of(settings.schoolId).schoolToggle?.let { spec ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = spec.valueOf(settings),
                            onCheckedChange = { vm.setSchoolToggle(spec, it) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(spec.title(t))
                            Text(
                                text = spec.subtitle(t),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
                    // 对必须手动登录的学校（如江苏大学）强制显示：这是唯一的登录途径
                    if (SHOW_WEB_LOGIN_ENTRY || requiresManualLogin(settings.schoolId)) {
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
                // 安装未知应用：应用内「检查更新 → 下载并安装」需要它；不开启也不影响其他功能
                PermissionRow(
                    name = t.permInstallName,
                    why = t.permInstallWhy,
                    granted = installAllowed,
                    actionLabel = t.gotoGrant,
                    onAction = {
                        runCatching {
                            installPermLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    },
                )
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

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：课程时间
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secPeriodTimes,
                t.secPeriodTimesSub,
                expanded = expPeriodTimes,
                onToggle = { expPeriodTimes = !expPeriodTimes },
                trailing = t.periodTimesCount(PeriodTimes.count),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (settings.periodTimesRaw.isBlank()) {
                            "${t.periodTimesDefaultTag} · ${t.periodTimesCount(PeriodTimes.count)}"
                        } else {
                            "${t.periodTimesCustomTag} · ${t.periodTimesCount(PeriodTimes.count)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPeriodTimes = true }) {
                        Text(t.btnEditPeriodTimes)
                    }
                }
                // 预览前四节，让用户确认当前生效的作息
                Text(
                    text = (1..minOf(4, PeriodTimes.count)).joinToString("  ") {
                        "${it}·${PeriodTimes.format(PeriodTimes.startOf(it))}-${
                            PeriodTimes.format(PeriodTimes.endOf(it))
                        }"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 节数小于课表实际最大节次时，周视图不会显示超出部分——提前提醒
                val maxPeriodUsed = remember(semesterCourses) {
                    semesterCourses.maxOfOrNull { it.endPeriod } ?: 0
                }
                if (maxPeriodUsed > PeriodTimes.count) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = t.periodTimesCountWarning(maxPeriodUsed, PeriodTimes.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：课程修改
        // 应对「课程信息已经变了、教务系统还没改」：可逐门课本地修正，也可一键还原 / 清除后重新同步
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secCourseEdits,
                t.secCourseEditsSub,
                expanded = expCourseEdits,
                onToggle = { expCourseEdits = !expCourseEdits },
                trailing = if (courseEditCount > 0) t.courseEditsCount(courseEditCount) else null,
            ) {
                Text(
                    text = t.courseEditsIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (courseEditCount == 0) {
                    Text(
                        text = t.courseEditsNone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = { showRestoreEditsConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t.btnRestoreCourseEdits) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showSyncClearEditsConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t.btnSyncClearCourseEdits) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.courseEditsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：节假日显示课表
        // 默认停课（与既有行为一致）；开启后节假日当天的课照常显示、提醒照常触发
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secHolidayCourses,
                t.secHolidayCoursesSub,
                expanded = expHoliday,
                onToggle = { expHoliday = !expHoliday },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.holidayCoursesSwitch, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.showHolidayCourses,
                        onCheckedChange = { vm.setShowHolidayCourses(it) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t.holidayCoursesNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                // 锁屏显示：关闭后常驻通知不会出现在锁屏上
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.secLockScreen)
                        Text(
                            text = t.secLockScreenSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.statusOnLockScreen,
                        onCheckedChange = { vm.setStatusOnLockScreen(it) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = t.lockScreenHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 部分系统（MIUI/HyperOS、EMUI 等）默认隐藏“静默通知”，只能由用户在系统里开启
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    com.kstudio.agenda.notif.StatusNotification.CHANNEL_ID,
                                )
                            }
                        )
                    }
                }) { Text(t.btnOpenNotifSettings) }
                Text(
                    text = t.statusHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 【选项卡 2】用户自定义：界面风格
        if (settingsTab == 2) item {
            CollapsibleSectionCard(
                t.secUiStyle,
                t.secUiStyleSub,
                expanded = expUiStyle,
                onToggle = { expUiStyle = !expUiStyle },
                trailing = if (settings.glassUi) t.uiStyleGlass else t.uiStyleDefault,
            ) {
                UiStyleOption(
                    label = t.uiStyleDefault,
                    sub = t.uiStyleDefaultSub,
                    selected = !settings.glassUi,
                    onClick = { vm.setUiStyle(com.kstudio.agenda.data.UI_STYLE_DEFAULT) },
                )
                UiStyleOption(
                    label = t.uiStyleGlass,
                    sub = t.uiStyleGlassSub,
                    selected = settings.glassUi,
                    onClick = { vm.setUiStyle(com.kstudio.agenda.data.UI_STYLE_GLASS) },
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

        // ---------------------------------------------------------- 【选项卡 3】数据与图片：数据备份与导入
        // 换机 / 重装前导出一份；导入为「按键覆盖」，本机密码与 AI Key 不受影响
        if (settingsTab == 3) item {
            CollapsibleSectionCard(
                t.secBackup,
                t.secBackupSub,
                expanded = expBackup,
                onToggle = { expBackup = !expBackup },
            ) {
                Text(
                    text = t.backupIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showExportChoice = true }) {
                        Text(t.btnBackupExport)
                    }
                    OutlinedButton(onClick = {
                        // 导入：先看默认位置（下载/KAgenda）有没有备份，有就让用户选，没有就直接开文件选择器
                        val found = vm.defaultBackups()
                        if (found.isEmpty()) {
                            runCatching {
                                backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        } else {
                            importChoices = found
                            showImportChoice = true
                        }
                    }) {
                        Text(t.btnBackupImport)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t.backupDefaultDirNote(com.kstudio.agenda.data.BackupStore.defaultLocationLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.backupIncludes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------- 【选项卡 3】数据与图片：文档导入
        if (settingsTab == 3) item {
            CollapsibleSectionCard(
                t.secDocs,
                t.secDocsSub,
                expanded = expDocs,
                onToggle = { expDocs = !expDocs },
            ) {
                Text(
                    text = t.docIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenDocImport) { Text(t.docTitle) }
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
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                // 官方入口：应用下载/介绍页 + 公开仓库（可点开浏览器）
                val uriHandler = LocalUriHandler.current
                listOf(
                    t.aboutOfficialSite to "https://20071009.xyz/KAgenda/",
                    t.aboutGithubRepo to "https://github.com/Krignd/KAgenda",
                ).forEach { (label, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { uriHandler.openUri(url) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = url.removePrefix("https://").removeSuffix("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        // ---------------------------------------------------------- 【选项卡 5】关于：用户反馈
        // 主题 + 内容 + 联系方式（选填）→ POST 到开发者站点（接口见部署指南）
        if (settingsTab == 5) item {
            CollapsibleSectionCard(
                t.secFeedback,
                t.secFeedbackSub,
                expanded = expFeedback,
                onToggle = { expFeedback = !expFeedback },
            ) {
                Text(
                    text = t.feedbackIntro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t.feedbackTopic,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val topics = listOf(t.topicBug, t.topicFeature, t.topicSchool, t.topicOther)
                    topics.forEachIndexed { idx, label ->
                        ChoiceChip(
                            label = label,
                            selected = feedbackTopic == idx,
                            onClick = { feedbackTopic = idx },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text(t.feedbackPlaceholder) },
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = feedbackContact,
                    onValueChange = { feedbackContact = it },
                    label = { Text(t.feedbackContact) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val key = listOf("bug", "feature", "school", "other")
                            .getOrElse(feedbackTopic) { "other" }
                        vm.sendFeedback(key, feedbackText, feedbackContact)
                    }) { Text(t.btnFeedbackSend) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t.feedbackIncludeNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // ---------------------------------------------------------- 【选项卡 5】关于：开发者工具入口（整行可点，行尾尖角符）
        if (settingsTab == 5) item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardGlassBorder(),
                shape = RoundedCornerShape(18.dp),
                color = com.kstudio.agenda.ui.components.cardBaseColor(),
                tonalElevation = com.kstudio.agenda.ui.components.cardTonalElevation(),
                shadowElevation = com.kstudio.agenda.ui.components.cardShadowElevation(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDevTools = true }
                        .padding(18.dp),
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
        // ---------------------------------------------------------- 【选项卡 5】关于：检查更新
        // 右侧动作随状态变化：
        //   刷新图标（点一下检查） → 「下载并安装」 → 「安装」（已下载但未安装）
        if (settingsTab == 5) item {
            val u = updateState
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardGlassBorder(),
                shape = RoundedCornerShape(18.dp),
                color = com.kstudio.agenda.ui.components.cardBaseColor(),
                tonalElevation = com.kstudio.agenda.ui.components.cardTonalElevation(),
                shadowElevation = com.kstudio.agenda.ui.components.cardShadowElevation(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = t.updateCheck,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        val sub = when (u) {
                            is UpdateUi.Checking -> t.updateChecking
                            is UpdateUi.UpToDate -> t.updateLatest
                            is UpdateUi.Available -> t.updateAvailable(
                                u.info.versionName,
                                sizeLabel(u.info.sizeBytes, t.updateSizeUnknown),
                            )
                            is UpdateUi.Downloading -> t.updateDownloading(u.percent)
                            is UpdateUi.Downloaded ->
                                t.updateDownloaded(sizeLabel(u.fileBytes, t.updateSizeUnknown))
                            is UpdateUi.Error -> t.updateFailed(u.message)
                            UpdateUi.Idle -> t.updateCurrent(BuildConfig.VERSION_NAME)
                        }
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (u is UpdateUi.Error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when (u) {
                        is UpdateUi.Checking -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        is UpdateUi.Downloading -> Text(
                            text = "${u.percent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is UpdateUi.Available -> TextButton(onClick = { AppUpdater.download(context) }) {
                            Text(t.updateDownloadAndInstall)
                        }
                        is UpdateUi.Downloaded -> TextButton(onClick = {
                            when (AppUpdater.install(context)) {
                                InstallStart.NeedPermission -> showInstallPermDialog = true
                                InstallStart.Unavailable -> vm.message(t.updatePackageInvalid)
                                InstallStart.Launched -> Unit
                            }
                        }) { Text(t.updateInstall) }
                        else -> IconButton(onClick = { AppUpdater.check(context, auto = false) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = t.updateCheck)
                        }
                    }
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

    // 仅还原课程修改（不联网）
    if (showRestoreEditsConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreEditsConfirm = false },
            title = { Text(t.askRestoreEditsTitle) },
            text = { Text(t.askRestoreEditsBody) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreEditsConfirm = false
                    vm.restoreCourseEdits()
                }) { Text(t.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreEditsConfirm = false }) { Text(t.cancel) }
            },
        )
    }

    // 与教务系统同步并清除所有课程修改
    if (showSyncClearEditsConfirm) {
        AlertDialog(
            onDismissRequest = { showSyncClearEditsConfirm = false },
            title = { Text(t.askSyncClearEditsTitle) },
            text = { Text(t.askSyncClearEditsBody) },
            confirmButton = {
                TextButton(onClick = {
                    showSyncClearEditsConfirm = false
                    vm.syncAndClearCourseEdits()
                }) { Text(t.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showSyncClearEditsConfirm = false }) { Text(t.cancel) }
            },
        )
    }

    // 课程时间自定义（节数与起止时间；保存后立即生效）
    if (showPeriodTimes) {
        // 「恢复默认」= 当前学校的作息预设（无预设的学校即内置默认作息，行为与以前一致）
        val school = com.kstudio.agenda.model.Schools.of(settings.schoolId)
        PeriodTimesDialog(
            restoreDefaults = SchoolFlows.of(school).periodPreset(school) ?: PeriodTimes.defaults,
            onDismiss = { showPeriodTimes = false },
            onSave = { list ->
                vm.setPeriodTimes(list)
                showPeriodTimes = false
            },
            onInvalid = { vm.message(t.msgPeriodTimesInvalid) },
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
                    // 入口保留（不隐藏），但如实提示可用性存疑（详见对话框内的说明）
                    Text(
                        text = t.adapterWarnTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
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

    // 导出备份：选默认位置或自选位置
    if (showExportChoice) {
        AlertDialog(
            onDismissRequest = { showExportChoice = false },
            title = { Text(t.backupExportTitle) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportChoice = false
                                vm.exportBackupToDefault()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.backupExportDefault, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = com.kstudio.agenda.data.BackupStore.defaultLocationLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportChoice = false
                                runCatching {
                                    backupExportLauncher.launch(
                                        t.backupFileName + "_" + java.time.LocalDate.now() + ".json"
                                    )
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.backupExportCustom, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportChoice = false }) { Text(t.cancel) }
            },
        )
    }

    // 导入备份：默认位置已有备份 → 列出让用户挑；也可改选其他文件
    if (showImportChoice) {
        AlertDialog(
            onDismissRequest = { showImportChoice = false },
            title = { Text(t.backupImportTitle) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = t.backupImportFound(
                            importChoices.size,
                            com.kstudio.agenda.data.BackupStore.defaultLocationLabel(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (e in importChoices) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportChoice = false
                                    pendingImport = e.uri
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = sizeLabel(e.sizeBytes, t.updateSizeUnknown) + " · " +
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(e.modifiedAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        showImportChoice = false
                        runCatching {
                            backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    }) { Text(t.backupImportPickOther) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportChoice = false }) { Text(t.cancel) }
            },
        )
    }

    // 导入备份：二次确认（会覆盖课表/日程/设置）
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(t.backupAskImportTitle) },
            text = { Text(t.backupAskImportBody) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImport
                    pendingImport = null
                    uri?.let { vm.importBackup(it) }
                }) { Text(t.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(t.cancel) }
            },
        )
    }

    // 安装更新但系统未允许本应用「安装未知应用」：引导用户去系统设置开启
    if (showInstallPermDialog) {
        AlertDialog(
            onDismissRequest = { showInstallPermDialog = false },
            title = { Text(t.updatePermissionTitle) },
            text = { Text(t.updatePermissionBody) },
            confirmButton = {
                TextButton(onClick = {
                    showInstallPermDialog = false
                    AppUpdater.openInstallPermissionSettings(context)
                }) { Text(t.updateOpenSettings) }
            },
            dismissButton = {
                TextButton(onClick = { showInstallPermDialog = false }) { Text(t.cancel) }
            },
        )
    }
}

/** 「添加学校」对话框：粘贴/生成适配代码（KagendaSchoolAdapter/2），校验后注册 */
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
                // 【可用性存疑，但不隐藏入口】通用/AI 生成的适配器依赖各校教务页面的
                // DOM 结构，实际往往无法直接可用；这里如实提示，用户可自行尝试。
                Text(
                    text = t.adapterWarnTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = t.adapterWarnBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/**
 * 学校图标：北航（school_buaa）、江苏大学（school_ujs）用内置校徽，
 * 其余学校用名称首字圆标。
 *
 * 两张校徽都是「白底 + 圆形徽标」的方形图，这里统一裁成圆形 ——
 * 与首字圆标的观感一致，也避免在深色/玻璃背景下露出白色方角。
 */
@Composable
private fun SchoolLogo(school: School, size: Dp) {
    val res = when (school.id) {
        "buaa" -> R.drawable.school_buaa
        "ujs" -> R.drawable.school_ujs
        else -> null
    }
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = school.name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
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
                    // 不在这里 clip/clickable：外围 clip 会把 Surface 自己的投影裁掉（卡片就看不出层次了），
                    // 点击与涟漪放到里面那行（本身就是被 Surface 圆角裁剪的）
                    .cardGlassBorder(),
                shape = RoundedCornerShape(18.dp),
                color = com.kstudio.agenda.ui.components.cardBaseColor(),
                tonalElevation = com.kstudio.agenda.ui.components.cardTonalElevation(),
                shadowElevation = com.kstudio.agenda.ui.components.cardShadowElevation(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(index) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    // 强调色箭头：让分类入口更容易被看到
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
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

/**
 * 界面风格选项：单选行（标题 + 说明），点击整行生效。
 */
@Composable
private fun UiStyleOption(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 课程时间自定义对话框：逐节编辑上课起止时间（节数可增删）。
 * 支持 "HH:mm"、"H:mm"、"HHmm" 三种写法；任一项无法解析时提示且不关闭对话框。
 *
 * @param restoreDefaults 「恢复默认」按钮要恢复到的作息：由调用方给出
 *        （当前学校的作息预设；无预设的学校为内置默认作息）。
 */
@Composable
private fun PeriodTimesDialog(
    restoreDefaults: List<Pair<LocalTime, LocalTime>>,
    onDismiss: () -> Unit,
    onSave: (List<Pair<LocalTime, LocalTime>>) -> Unit,
    onInvalid: () -> Unit,
) {
    val t = LocalStrings.current
    val initial = remember { PeriodTimes.current() }
    val starts = remember {
        mutableStateListOf<String>().apply {
            addAll(initial.map { PeriodTimes.format(it.first) })
        }
    }
    val ends = remember {
        mutableStateListOf<String>().apply {
            addAll(initial.map { PeriodTimes.format(it.second) })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.periodTimesTitle) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = t.periodTimesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                // 当前节数（可增删，1..24 节）
                Text(
                    text = t.periodTimesCount(starts.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                for (i in starts.indices) {
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t.periodTimesRowFmt.format(i + 1),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(52.dp),
                        )
                        TimeField(starts[i], Modifier.weight(1f)) { starts[i] = it }
                        Text("–", modifier = Modifier.padding(horizontal = 4.dp))
                        TimeField(ends[i], Modifier.weight(1f)) { ends[i] = it }
                        // 删减节次（至少保留 1 节）
                        IconButton(
                            onClick = {
                                if (starts.size > PeriodTimes.MIN_COUNT) {
                                    starts.removeAt(i)
                                    ends.removeAt(i)
                                }
                            },
                            enabled = starts.size > PeriodTimes.MIN_COUNT,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = t.periodTimesRemoveOne,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 新增节次（默认作息向后推算，可再手改）
                OutlinedButton(
                    onClick = {
                        if (starts.size < PeriodTimes.MAX_COUNT) {
                            val p = PeriodTimes.defaultPairOf(starts.size + 1)
                            starts.add(PeriodTimes.format(p.first))
                            ends.add(PeriodTimes.format(p.second))
                        }
                    },
                    enabled = starts.size < PeriodTimes.MAX_COUNT,
                ) { Text(t.periodTimesAddOne) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = starts.indices.map { i ->
                    val s = parseLooseTime(starts[i])
                    val e = parseLooseTime(ends[i])
                    if (s == null || e == null) null else s to e
                }
                if (list.any { it == null }) onInvalid() else onSave(list.filterNotNull())
            }) { Text(t.save) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    // 恢复默认：回到当前学校的作息预设（无预设则内置默认作息）
                    starts.clear()
                    ends.clear()
                    restoreDefaults.forEach { p ->
                        starts.add(PeriodTimes.format(p.first))
                        ends.add(PeriodTimes.format(p.second))
                    }
                }) { Text(t.periodTimesRestoreDefault) }
                TextButton(onClick = onDismiss) { Text(t.cancel) }
            }
        },
    )
}

/** 单个时间输入框（课程时间对话框内使用） */
@Composable
private fun TimeField(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier,
    )
}

/** 宽松解析时间："8:00" / "08:00" / "0800" / "800" / "8.00" / 全角冒号 均可 */
private fun parseLooseTime(raw: String): LocalTime? {
    val s = raw.trim().replace('：', ':').replace('.', ':')
    if (s.isEmpty()) return null
    if (s.contains(':')) {
        val h = s.substringBefore(':').trim().toIntOrNull() ?: return null
        val m = s.substringAfter(':').trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
    val d = s.filter { it.isDigit() }
    return when (d.length) {
        4 -> {
            val h = d.substring(0, 2).toInt()
            val m = d.substring(2, 4).toInt()
            if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
        }
        3 -> {
            val h = d.substring(0, 1).toInt()
            val m = d.substring(1, 3).toInt()
            if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
        }
        else -> null
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
