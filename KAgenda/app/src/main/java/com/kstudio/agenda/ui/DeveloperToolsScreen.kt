@file:OptIn(ExperimentalMaterial3Api::class)

package com.kstudio.agenda.ui

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.data.SyncUi
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.notif.Notifier
import com.kstudio.agenda.notif.ReminderReceiver
import com.kstudio.agenda.notif.ReminderScheduler
import com.kstudio.agenda.ui.components.SectionCard
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「开发者工具」页：独立页面打开（设置页仅保留一个入口）。
 * 包含：运行日志、诊断信息、通知测试（验证提醒链路）、提醒调度工具、重置（自设置页迁入）。
 */
@Composable
fun DeveloperToolsScreen(vm: AppViewModel, onClose: () -> Unit) {
    val t = LocalStrings.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val sync by vm.syncState.collectAsState()
    var showLog by remember { mutableStateOf(false) }
    var notifStatus by remember { mutableStateOf("") }
    var reminderCount by remember { mutableStateOf(ReminderScheduler.scheduledCount(context)) }
    var refreshTick by remember { mutableStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    // 延迟 0.6 秒再读已排提醒数，等待异步重排完成
    LaunchedEffect(refreshTick) {
        delay(600)
        reminderCount = ReminderScheduler.scheduledCount(context)
    }

    // 立即发送测试通知（走 Notifier 直发链路）
    fun sendTestNotification() {
        Notifier.show(
            context = context,
            title = "测试课程 · 高等数学",
            room = "教3-201",
            timeRange = "08:00-08:45",
            periodLabel = "第1节",
            dateIso = "test-" + System.currentTimeMillis(),
        )
        notifStatus = t.testNotifSent
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendTestNotification()
        else notifStatus = t.notifNo
    }

    // 约 15 秒后触发测试提醒（完整闹钟→广播→通知链路）
    fun scheduleTestReminder() {
        val am = context.getSystemService(AlarmManager::class.java)
        if (am == null) {
            notifStatus = t.notifNo
            return
        }
        val fireAt = System.currentTimeMillis() + 15_000L
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, "测试提醒 · 高等数学")
            putExtra(ReminderReceiver.EXTRA_ROOM, "教3-201")
            putExtra(ReminderReceiver.EXTRA_TIME_RANGE, "08:00-08:45")
            putExtra(ReminderReceiver.EXTRA_PERIOD_LABEL, "第1节")
            putExtra(ReminderReceiver.EXTRA_DATE, "test-" + fireAt)
            putExtra(ReminderReceiver.EXTRA_TEST, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            992211,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (ReminderScheduler.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 60_000L, pi)
        }
        notifStatus = t.testReminderScheduled
    }

    // 滚动状态提升到「运行日志」早退之前：从日志页返回后不会跳回顶部
    val contentScroll = rememberScrollState()

    if (showLog) {
        LogScreen(onClose = { showLog = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        // 标题行紧贴「K日程」标题下方（不再用独立 Scaffold/AppBar，避免上下两个标题间距过大）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t.back)
            }
            Text(t.secDev, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(contentScroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------- 通知测试 ----------------
            SectionCard(t.secNotifTest, t.secNotifTestSub) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (!Notifier.hasPermission(context)) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            sendTestNotification()
                        }
                    }) {
                        Text(t.sendTestNotifBtn)
                    }
                    OutlinedButton(onClick = { scheduleTestReminder() }) {
                        Text(t.sendTestReminderBtn)
                    }
                }
                if (notifStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = notifStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ---------------- 提醒调度工具 ----------------
            SectionCard(t.secReminderTools, null) {
                Text(
                    text = t.reminderCountLabel(reminderCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.rescheduleReminders()
                        refreshTick++
                    }) {
                        Text(t.rescheduleBtn)
                    }
                    OutlinedButton(onClick = {
                        ReminderScheduler.cancelAll(context)
                        reminderCount = 0
                        notifStatus = t.alarmsCancelled
                    }) {
                        Text(t.cancelAlarmsBtn)
                    }
                }
            }

            // ---------------- 运行日志 ----------------
            SectionCard(t.logTitle, t.logDesc) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showLog = true }) {
                        Text(t.viewShareLog)
                    }
                    OutlinedButton(onClick = { AppLog.clear() }) {
                        Text(t.clearLogBtn)
                    }
                }
            }

            // ---------------- 诊断信息 ----------------
            SectionCard(t.diagTitle, null) {
                InfoRow(t.diagVersion, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.padding(vertical = 3.dp))
                InfoRow(t.diagSync, syncLabel(sync, t.syncNotLoggedIn, t.syncRunning, t.diagNone))
                Spacer(Modifier.padding(vertical = 3.dp))
                InfoRow(
                    t.diagLastSync,
                    if (settings.lastSyncAtMillis > 0L) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(settings.lastSyncAtMillis))
                    } else t.neverSynced,
                )
            }

            // ---------------- 重置（自设置页迁入） ----------------
            SectionCard(t.secReset, t.secResetSub) {
                Text(
                    text = t.resetClearDataDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showClearDataConfirm = true }) {
                    Text(t.resetClearData)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t.resetClearCacheDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showClearCacheConfirm = true }) {
                    Text(t.resetClearCache)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = t.resetAllDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showResetConfirm = true }) {
                    Text(t.resetAll)
                }
            }
        }
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text(t.askClearDataTitle) },
            text = { Text(t.askClearDataBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    vm.clearAgendaData()
                }) { Text(t.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) { Text(t.cancel) }
            },
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(t.askClearCacheTitle) },
            text = { Text(t.askClearCacheBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheConfirm = false
                    vm.clearCacheAndLogs()
                }) { Text(t.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text(t.cancel) }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(t.askResetTitle) },
            text = { Text(t.askResetBody) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    vm.resetApp()
                }) { Text(t.resetAll) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(t.cancel) }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun syncLabel(
    sync: SyncUi,
    notLoggedIn: String,
    running: String,
    none: String,
): String = when (sync) {
    is SyncUi.Success -> "✓"
    is SyncUi.Error -> sync.message
    is SyncUi.NeedLogin -> if (sync.message.isNotBlank()) sync.message else notLoggedIn
    SyncUi.Running -> running
    SyncUi.Idle -> none
}
