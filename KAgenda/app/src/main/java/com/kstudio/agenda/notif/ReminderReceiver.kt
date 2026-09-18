package com.kstudio.agenda.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kstudio.agenda.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 课前提醒闹钟触发入口。
 * 触发时再次校验“提醒开关”，关闭则静默忽略（避免残留闹钟打扰）。
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_ROOM = "room"
        const val EXTRA_TIME_RANGE = "timeRange"
        const val EXTRA_PERIOD_LABEL = "periodLabel"
        const val EXTRA_DATE = "date"

        /** 测试提醒标记：为 true 时不受“提醒开关”限制（供开发者工具验证链路） */
        const val EXTRA_TEST = "testMode"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore.read(appContext)
                val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
                if (!settings.reminderEnabled && !isTest) return@launch
                Notifier.show(
                    context = appContext,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "课程",
                    room = intent.getStringExtra(EXTRA_ROOM) ?: "",
                    timeRange = intent.getStringExtra(EXTRA_TIME_RANGE) ?: "",
                    periodLabel = intent.getStringExtra(EXTRA_PERIOD_LABEL) ?: "",
                    dateIso = intent.getStringExtra(EXTRA_DATE) ?: "",
                )
                // 提醒触发后，“下一节课”已变化：顺手刷新桌面小组件与常驻通知
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
