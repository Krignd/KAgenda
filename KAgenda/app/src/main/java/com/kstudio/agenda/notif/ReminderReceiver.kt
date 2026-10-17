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
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_ALARM = "alarm"
        const val EXTRA_NOTIFY = "notify"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ROOM = "room"
        const val EXTRA_TIME_RANGE = "timeRange"
        const val EXTRA_PERIOD_LABEL = "periodLabel"
        const val EXTRA_DATE = "date"
        /** true=自建日程/计划提醒（与课程提醒区分标题文案） */
        const val EXTRA_AGENDA = "agenda"

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
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
                var allowNotify = intent.getBooleanExtra(EXTRA_NOTIFY, true)
                var allowAlarm = intent.getBooleanExtra(EXTRA_ALARM, false)
                if (!isTest) {
                    if (eventId != null) {
                        com.kstudio.agenda.data.AgendaStore.ensureLoaded(appContext)
                        val event = com.kstudio.agenda.data.AgendaStore.events.value.firstOrNull { it.id == eventId } ?: return@launch
                        val policy = com.kstudio.agenda.model.ReminderPolicy.forEvent(event, settings.reminderEnabled, settings.remindAgenda, settings.alarmEnabled, settings.leadMinutes)
                        if (!policy.enabled) return@launch
                        allowNotify = allowNotify && policy.notify
                        allowAlarm = allowAlarm && policy.alarm
                    } else {
                        if (!settings.reminderEnabled) return@launch
                        allowAlarm = allowAlarm && settings.alarmEnabled
                    }
                }
                if (allowAlarm) {
                    val alarmIntent = Intent(appContext, AgendaAlarmService::class.java)
                        .putExtra(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE))
                        .putExtra(EXTRA_TIME_RANGE, intent.getStringExtra(EXTRA_TIME_RANGE))
                    runCatching { androidx.core.content.ContextCompat.startForegroundService(appContext, alarmIntent) }
                        .onFailure { com.kstudio.agenda.util.AppLog.e("Alarm", "Unable to start alarm", it) }
                }
                if (allowNotify) Notifier.show(
                    context = appContext,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "课程",
                    room = intent.getStringExtra(EXTRA_ROOM) ?: "",
                    timeRange = intent.getStringExtra(EXTRA_TIME_RANGE) ?: "",
                    periodLabel = intent.getStringExtra(EXTRA_PERIOD_LABEL) ?: "",
                    dateIso = intent.getStringExtra(EXTRA_DATE) ?: "",
                    agenda = intent.getBooleanExtra(EXTRA_AGENDA, false),
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
