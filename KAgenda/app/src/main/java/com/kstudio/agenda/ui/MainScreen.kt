@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kstudio.agenda.R
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.data.WebScheduleEngine
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.notif.Notifier
import java.time.LocalDate

/**
 * 【已隐藏保留】应用内 AI 助手悬浮按钮（可拖拽的圆形 DeepSeek 按钮）开关。
 * 默认隐藏：开启系统悬浮球时它多余；未开启悬浮球时也不应出现在右下角（易被误认为悬浮球已开启）。
 * 改为 true 即可恢复（按钮代码完整保留）。
 */
private const val SHOW_IN_APP_AI_BUTTON = false

private enum class HomeTab {
    Schedule,
    Plan,
    Ongoing,
    Settings,
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
    // 内容区尺寸（用于把可拖拽悬浮按钮限制在屏幕内）
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val sync by vm.syncState.collectAsState()
    val syncActive = sync is SyncUi.Running || sync is SyncUi.NeedLogin
    val settings by vm.settings.collectAsState()
    val aiSurface by AiSurface.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val t = LocalStrings.current

    // 通知权限（Android 13+）；存储权限（Android 8.0–9 导出图片到相册需要）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (!Notifier.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // 通知 / 小组件点击带入的启动请求：打开 AI 助手，或定位到指定日期并闪烁反馈
    LaunchedEffect(launch?.id) {
        val l = launch ?: return@LaunchedEffect
        // 只有「定位日期」类请求才需要回到日程表页；
        // 打开 AI 助手（顶栏图标 / 常驻通知 / 悬浮球）不应改动当前页签，
        // 否则在计划/进行中/设置页点悬浮球会被强行跳回日程表
        if (l.focusEpochDay != null) tab = HomeTab.Schedule
        if (l.quickAdd) showQuickAdd = true
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(t.appTitle, style = MaterialTheme.typography.titleLarge)
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
                    IconButton(onClick = { showQuickAdd = true }) {
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
            NavigationBar {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                // 【已隐藏保留】网页登录入口：显隐由 SettingsScreen 内的 SHOW_WEB_LOGIN_ENTRY 控制
                HomeTab.Settings -> SettingsScreen(
                    vm = vm,
                    onWebLogin = {
                        webLoginLauncher.launch(Intent(context, WebLoginActivity::class.java))
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
