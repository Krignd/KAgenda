@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AppUpdater
import com.kstudio.agenda.data.InstallStart
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.data.UpdateUi
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.i18n.LocalStrings
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 【已隐藏保留】应用内 AI 助手悬浮按钮（可拖拽的圆形 DeepSeek 按钮）开关。
 * 默认隐藏：开启系统悬浮球时它多余；未开启悬浮球时也不应出现在右下角（易被误认为悬浮球已开启）。
 * 改为 true 即可恢复（按钮代码完整保留）。
 */
private const val SHOW_IN_APP_AI_BUTTON = false

private enum class HomeTab {
    Schedule,
    Plan,
    /** 【新建】学校页（底部栏中间，图标为当前学校校徽）；目前只是占位页 */
    School,
    Ongoing,
    Settings,
}

/** 学校页占位：校徽 + 校名 + 「暂未完成」，具体功能后续再做 */
@Composable
private fun SchoolPlaceholderScreen(schoolId: String) {
    val t = LocalStrings.current
    val school = com.kstudio.agenda.model.Schools.of(schoolId)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SchoolLogo(school, 72.dp)
            Spacer(Modifier.size(12.dp))
            Text(text = school.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                text = t.comingSoon,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MainScreen(
    vm: AppViewModel,
    launch: LaunchRequest? = null,
    onLaunchHandled: () -> Unit = {},
) {
    // 旋转屏幕/系统重建后保持当前页签与弹窗状态
    var tab by rememberSaveable { mutableStateOf(HomeTab.Schedule) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    // 文档导入页（设置入口 / 其他应用传入文档时打开）
    var docImportOpen by rememberSaveable { mutableStateOf(false) }
    var docImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 内容区尺寸（用于把可拖拽悬浮按钮限制在屏幕内）
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val sync by vm.syncState.collectAsState()
    val syncActive = sync is SyncUi.Running || sync is SyncUi.NeedLogin
    val settings by vm.settings.collectAsState()
    // 应用内更新状态（顶栏小字与「设置 → 关于」共用同一份）
    val updateState by AppUpdater.state.collectAsState()
    var showInstallPermDialog by remember { mutableStateOf(false) }
    // 下载完成后提示一次（含包体大小）；失败时也提示一次
    var announcedUpdate by remember { mutableStateOf(0) }
    var announcedError by remember { mutableStateOf("") }
    val aiSurface by AiSurface.state.collectAsState()
    val exportNeedsPermission by vm.exportNeedsPermission.collectAsState()
    // 应用内提示（Snackbar）：自己维护一个堆叠列表 —— Material3 的 SnackbarHost 一次只显示一条，
    // 新提示会把旧的顶掉；这里改成：旧提示保持在原位、新提示出现在它下方（于是旧的整体上移），
    // 每条仍按原来的时长（4 秒）自动消失。
    val toasts = remember { mutableStateListOf<Pair<Long, String>>() }
    val context = LocalContext.current
    val t = LocalStrings.current

    /** 带单位的包体大小（拿不到时显示「大小未知」） */
    fun sizeText(bytes: Long): String =
        AppUpdater.formatBytes(bytes).ifBlank { t.updateSizeUnknown }

    /** 拉起安装；缺「安装未知应用」权限时弹窗引导 */
    fun startInstall() {
        when (AppUpdater.install(context)) {
            InstallStart.NeedPermission -> showInstallPermDialog = true
            InstallStart.Unavailable -> vm.message(t.updatePackageInvalid)
            InstallStart.Launched -> Unit
        }
    }

    // 启动时不申请任何权限（安装后开箱即用）：
    // · 通知权限：默认关闭的「课前提醒」或「常驻通知」由用户在设置里开启时才申请；
    // · 存储权限：仅 Android 8.0–9 导出图片到相册需要，改为点击保存时再申请（见 vm.withExportPermission）。
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onExportPermissionResult(granted) }
    LaunchedEffect(exportNeedsPermission) {
        if (exportNeedsPermission) {
            exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // 【已隐藏保留】网页登录启动器：入口已在设置页隐藏（SettingsScreen.SHOW_WEB_LOGIN_ENTRY=false），
    // 此启动器与回调保留未删除，恢复入口时无需改动
    val webLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val loggedIn =
                result.data?.getBooleanExtra(WebLoginActivity.EXTRA_WEB_LOGIN_OK, false) == true
            vm.onWebLoginFinished(loggedIn)
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val key = System.nanoTime()
            toasts.add(key to msg)
            // 每条提示独立计时（与原来 Snackbar 的 4 秒一致）
            launch {
                delay(4000)
                toasts.removeAll { it.first == key }
            }
        }
    }

    // 启动时静默检查一次更新（内部按 6 小时节流；失败不打扰用户）。
    // 可在设置里关闭（默认开）—— 先等设置读到再判断，避免读取未完成时误判。
    val updateSettings by vm.settings.collectAsState()
    LaunchedEffect(updateSettings.autoCheckUpdate) {
        if (updateSettings.autoCheckUpdate) AppUpdater.check(context, auto = true)
    }
    // 下载完成 / 更新出错时各提示一次（包体大小取自实际文件）
    LaunchedEffect(updateState) {
        when (val s = updateState) {
            is UpdateUi.Downloaded -> if (s.info.versionCode != announcedUpdate) {
                announcedUpdate = s.info.versionCode
                vm.message(t.updateDownloaded(sizeText(s.fileBytes)))
            }
            is UpdateUi.Error -> if (s.message != announcedError) {
                announcedError = s.message
                vm.message(t.updateFailed(s.message))
            }
            else -> Unit
        }
    }


    // 通知 / 小组件点击带入的启动请求：打开 AI 助手，或定位到指定日期并闪烁反馈
    LaunchedEffect(launch?.id) {
        val l = launch ?: return@LaunchedEffect
        // 只有「定位日期」类请求才需要回到日程表页；
        // 打开 AI 助手（顶栏图标 / 常驻通知 / 悬浮球）不应改动当前页签，
        // 否则在计划/进行中/设置页点悬浮球会被强行跳回日程表
        if (l.focusEpochDay != null) tab = HomeTab.Schedule
        if (l.quickAdd) showQuickAdd = true
        // 其他应用传入文档：直接打开文档导入页
        if (l.docUri != null) {
            docImportUri = l.docUri
            docImportOpen = true
        }
        l.focusEpochDay?.let { day ->
            val date = LocalDate.ofEpochDay(day)
            // 小组件点击：优先定位“此刻正在进行”的课程（小组件状态可能滞后），
            // 没有正在进行的课程时再按小组件传来的目标定位
            if (l.fromWidget) vm.focusFromWidget(date, l.focusTitle)
            else vm.requestFocus(date, l.focusTitle)
        }
        onLaunchHandled()
    }

    // AI 助手界面互斥（同一时刻只保留一个）：悬浮球面板打开 → 收起应用内对话框；
    // 应用内对话框打开 → 通知悬浮球收起面板；两者几乎同时打开时优先悬浮球
    LaunchedEffect(aiSurface) {
        if (aiSurface == AiSurface.Kind.Overlay) showQuickAdd = false
    }
    LaunchedEffect(showQuickAdd) {
        if (showQuickAdd) {
            if (!AiSurface.openInApp()) showQuickAdd = false
        } else {
            AiSurface.close(AiSurface.Kind.InApp)
        }
    }

    Scaffold(
        // 液态玻璃：容器透明，露出底层的渐变背景（卡片/导航栏半透明形成毛玻璃观感）
        containerColor = if (settings.glassUi) Color.Transparent
        else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (settings.glassUi) Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
                title = {
                    // 点「K日程」标题或下面的同步状态小字 = 手动同步（与右上角刷新按钮等价）
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.syncNow() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.appTitle, style = MaterialTheme.typography.titleLarge)
                            // 有可用更新时在「K日程」右侧显示小字：
                            //   有新版本 → (有更新!)  点一下开始下载（提示里写明包体大小）
                            //   下载中   → (下载中 n%)
                            //   已下载   → (点击安装)
                            val hint: String? = when (val u = updateState) {
                                is UpdateUi.Available -> t.updateHintAvailable
                                is UpdateUi.Downloading -> t.updateHintDownloading(u.percent)
                                is UpdateUi.Downloaded -> t.updateHintInstall
                                else -> null
                            }
                            if (hint != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        // 内层点击会消费事件，不会触发外层的「手动同步」
                                        .clickable {
                                            when (val u = updateState) {
                                                is UpdateUi.Available -> {
                                                    vm.message(
                                                        t.updateStartDownload(
                                                            sizeText(u.info.sizeBytes),
                                                        ),
                                                    )
                                                    AppUpdater.download(context)
                                                }
                                                is UpdateUi.Downloaded -> startInstall()
                                                else -> Unit
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                        when (val s = sync) {
                            is SyncUi.Success -> Column {
                                Text(
                                    t.syncSyncedAt + android.text.format.DateUtils.getRelativeTimeSpanString(s.atMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 需要用户留意的提示（如：手动登录的新密码未能校验）
                                if (s.notice.isNotBlank()) {
                                    Text(
                                        text = s.notice,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            is SyncUi.Error -> Text(
                                s.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            is SyncUi.NeedLogin -> Text(
                                text = s.message.ifBlank { t.syncNotLoggedIn },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.message.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            SyncUi.Running -> Text(
                                t.syncRunning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SyncUi.Idle -> Unit
                        }
                    }
                },
                actions = {
                    if (sync is SyncUi.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    // AI 识别完成、用户还没查看时，在 DeepSeek 图标左侧显示「!」角标（点它直接看结果）
                    if (vm.aiRun.collectAsState().value.unread) {
                        IconButton(onClick = {
                            vm.markAiResultSeen()
                            showQuickAdd = true
                        }) {
                            Text(
                                text = "!",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        }
                    }
                    IconButton(onClick = {
                        // 打开界面即视为已查看：熄灭「!」角标
                        vm.markAiResultSeen()
                        showQuickAdd = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_deepseek),
                            contentDescription = t.qaTitle,
                        )
                    }
                    IconButton(onClick = { vm.syncNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = t.refresh)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = if (settings.glassUi) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                } else {
                    NavigationBarDefaults.containerColor
                },
            ) {
                NavigationBarItem(
                    selected = tab == HomeTab.Schedule,
                    onClick = { tab = HomeTab.Schedule },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(t.tabSchedule) },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.Plan,
                    onClick = { tab = HomeTab.Plan },
                    icon = { Icon(Icons.Filled.EventNote, contentDescription = null) },
                    label = { Text(t.tabPlan) },
                )
                // 中间的「学校」页签：图标用当前所选学校的校徽（具体页面待做，先显示「暂未完成」）
                NavigationBarItem(
                    selected = tab == HomeTab.School,
                    onClick = { tab = HomeTab.School },
                    icon = { SchoolLogo(com.kstudio.agenda.model.Schools.of(settings.schoolId), 24.dp) },
                    label = { Text(t.tabSchool) },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.Ongoing,
                    onClick = { tab = HomeTab.Ongoing },
                    icon = { Icon(Icons.Filled.EventAvailable, contentDescription = null) },
                    label = { Text(t.tabOngoing) },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.Settings,
                    onClick = { tab = HomeTab.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(t.tabSettings) },
                )
            }
        },
        // 堆叠展示：先加入的在上、后加入的在下方（旧提示整体上移，而不是被顶掉）
        snackbarHost = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                toasts.forEach { (_, text) ->
                    Snackbar(modifier = Modifier.fillMaxWidth()) { Text(text) }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { contentSize = it },
        ) {
            when (tab) {
                // 日/周/月视图统一放在「日程表」页内，顶部切换
                HomeTab.Schedule -> ScheduleScreen(vm)
                // 「计划」：与日程表并列，用于非学校规定的个人计划
                HomeTab.Plan -> PlanScreen(vm)
                // 「进行中」：专门展示正在进行的长日程
                HomeTab.Ongoing -> OngoingScreen(vm)
                // 【新建】「学校」页：占位（校徽 + 校名 + 「暂未完成」），等后续再做具体内容
                HomeTab.School -> SchoolPlaceholderScreen(settings.schoolId)
                // 【已隐藏保留】网页登录入口：显隐由 SettingsScreen 内的 SHOW_WEB_LOGIN_ENTRY 控制
                HomeTab.Settings -> SettingsScreen(
                    vm = vm,
                    onWebLogin = {
                        webLoginLauncher.launch(Intent(context, WebLoginActivity::class.java))
                    },
                    onOpenDocImport = {
                        docImportUri = null
                        docImportOpen = true
                    },
                )
            }
            // 【已隐藏保留】AI 助手悬浮按钮（应用内可拖拽的圆形 DeepSeek 按钮）。
            // 隐藏原因：开启系统悬浮球时它本就多余；未开启悬浮球时也不应在右下角出现这个圆形图标
            // （用户会误以为悬浮球开着自己没开）。应用内仍可用顶栏的 DeepSeek 图标或常驻通知入口。
            // 如需恢复，把 SHOW_IN_APP_AI_BUTTON 改为 true 即可（代码完整保留）。
            if (SHOW_IN_APP_AI_BUTTON) {
                val overlayActive = settings.floatingBall && Settings.canDrawOverlays(context)
                if (!overlayActive) {
                    var fabOffset by remember { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp)
                            .offset {
                                IntOffset(
                                    Math.round(fabOffset.x).toInt(),
                                    Math.round(fabOffset.y).toInt(),
                                )
                            }
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .pointerInput(contentSize) {
                                val fabPx = 52.dp.toPx()
                                val marginPx = 18.dp.toPx()
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    // 边界收敛：不允许把按钮拖出内容区（原来无限制，拖出屏幕后再也点不到）
                                    val minX =
                                        -(contentSize.width - fabPx - marginPx * 2).coerceAtLeast(0f)
                                    val minY =
                                        -(contentSize.height - fabPx - marginPx * 2).coerceAtLeast(0f)
                                    fabOffset = Offset(
                                        (fabOffset.x + drag.x).coerceIn(minX, 0f),
                                        (fabOffset.y + drag.y).coerceIn(minY, 0f),
                                    )
                                }
                            }
                            .clickable { showQuickAdd = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_deepseek),
                            contentDescription = t.qaTitle,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            // 无界面 WebView 宿主：仅在同步期间挂载。
            // WebView 首次创建会阻塞主线程数百毫秒，按需创建可显著改善启动与切视图流畅度；
            // 同步任务自身也会在需要时创建/复用实例（obtainHostView 复用同一个 WebView）
            if (syncActive) {
                HiddenWebViewHost(
                    engine = vm.engine,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    if (showQuickAdd) {
        AiQuickAddDialog(vm) { showQuickAdd = false }
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

    // 同步后：课程修改与教务系统都不一致时询问（自动同步也会触发）
    val editConflicts by vm.editConflicts.collectAsState()
    if (editConflicts.isNotEmpty()) {
        CourseEditConflictDialog(
            conflicts = editConflicts,
            onFollow = { vm.resolveEditConflict(it, followAcademic = true) },
            onKeep = { vm.resolveEditConflict(it, followAcademic = false) },
            onFollowAll = { vm.resolveAllEditConflicts(followAcademic = true) },
            onKeepAll = { vm.resolveAllEditConflicts(followAcademic = false) },
            onLater = { vm.dismissEditConflicts() },
        )
    }

    // 文档导入：独立整页（从设置入口或其他应用传入文档打开）
    if (docImportOpen) {
        DocumentImportScreen(
            vm = vm,
            initialUri = docImportUri,
            onClose = {
                docImportOpen = false
                docImportUri = null
            },
        )
    }
}

@Composable
private fun HiddenWebViewHost(engine: WebScheduleEngine, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { _ ->
            engine.obtainHostView().apply {
                alpha = 0f
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(2, 2)
                engine.attach(this)
            }
        },
        modifier = modifier.size(2.dp),
    )
}
