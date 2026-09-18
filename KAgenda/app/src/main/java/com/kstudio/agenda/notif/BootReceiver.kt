package com.kstudio.agenda.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机 / 应用更新 / 系统时间变更后恢复提醒调度（系统会清空或错配闹钟） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(appContext)
                // 开机后恢复小组件与常驻通知的定时刷新链
                runCatching {
                    com.kstudio.agenda.widget.NextClassWidgetUpdater.updateAndSchedule(appContext)
                }
                runCatching {
                    com.kstudio.agenda.notif.StatusNotification.refresh(appContext)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
