package com.kstudio.agenda.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.AppSettings
import com.kstudio.agenda.data.ScheduleCache
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 常驻状态通知：
 * - 在锁屏与通知栏常驻显示（静默、低打扰、隐形于下拉时可见）；
 * - 内容由「设置 → 常驻通知」勾选：下一节课与当前课程 / 当前计划 / 下一日程；
 * - 刷新链与小组件类似（临近上课每分钟，空闲降频），随开关自动启停。
 */
object StatusNotification {

    const val CHANNEL_ID = "status_persistent"

    private const val NOTIF_ID = 925200
    private const val REQUEST_CODE = 925201

    /** 「AI 快速添加」文本框入口（与状态行同频道、独立通知） */
    private const val AI_NOTIF_ID = 925210
    private const val AI_REQUEST_CODE = 925211

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            AppText.current.statusChannelName,
            android.app.NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = AppText.current.statusChannelDesc
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** 刷新（更新内容 + 安排下一次闹钟）——Receiver / VM / 仓库在合适时机调用 */
    suspend fun refresh(context: Context) {
        val settings = SettingsStore.read(context)
        runCatching { updateWith(context, settings) }
        scheduleAlarm(context, settings)
    }

    /** 仅更新通知内容（不重排闹钟） */
    suspend fun update(context: Context) {
        runCatching { updateWith(context, SettingsStore.read(context)) }
    }

    private suspend fun updateWith(context: Context, settings: AppSettings) {
        val nm = NotificationManagerCompat.from(context)
        if (!settings.statusNotifEnabled || !Notifier.hasPermission(context)) {
            nm.cancel(NOTIF_ID)
            nm.cancel(AI_NOTIF_ID)
            return
        }
        ensureChannel(context)
        // 「AI 快速添加」入口：文本框样式，点按直达 App 的 AI 添加界面（无状态行时也常驻）
        notifyAiEntry(context, nm)
        val sources = settings.statusNotifSources.split(',').map { it.trim() }
        val lines = buildLines(context, sources)
        if (lines.isEmpty()) {
            nm.cancel(NOTIF_ID)
            return
        }
        val pi = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(AppText.current.appTitle)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
        }
    }

    /** 常驻通知中的「AI 快速添加」：显示为一个文本框，点按打开 App 的 AI 添加界面 */
    private fun notifyAiEntry(context: Context, nm: NotificationManagerCompat) {
        val t = AppText.current
        val pi = PendingIntent.getActivity(
            context,
            AI_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_QUICK_ADD, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val rv = RemoteViews(context.packageName, R.layout.notification_ai_quick_add).apply {
            setTextViewText(R.id.notif_ai_hint, t.statusAiEntryHint)
            setOnClickPendingIntent(R.id.notif_ai_root, pi)
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.statusAiEntryTitle)
            .setContentText(t.statusAiEntryHint)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(AI_NOTIF_ID, notif)
        } catch (_: SecurityException) {
        }
    }

    private fun scheduleAlarm(context: Context, settings: AppSettings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, StatusRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!settings.statusNotifEnabled) return
        val delayMs = nextDelayMs(context)
        val triggerAt = System.currentTimeMillis() + delayMs
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun nextDelayMs(context: Context): Long {
        val now = LocalDateTime.now()
        val semester = ScheduleCache.load(context)
        if (semester != null && findCurrentCourse(semester, now) != null) return 60_000L
        if (semester != null) {
            val next = findNextCourse(semester, now)
            if (next != null && Duration.between(now, next.third).toMinutes() <= 120) return 60_000L
        }
        return 30 * 60_000L
    }

    // ------------------------------------------------------------ 内容

    private suspend fun buildLines(context: Context, sources: List<String>): List<String> {
        val t = AppText.current
        val lines = mutableListOf<String>()
        val now = LocalDateTime.now()
        val semester = ScheduleCache.load(context)

        if ("course" in sources && semester != null) {
            val current = findCurrentCourse(semester, now)
            if (current != null) {
                val end = PeriodTimes.format(PeriodTimes.endOf(current.endPeriod))
                lines.add(t.statusInClassFmt(current.title, end))
            } else {
                val next = findNextCourse(semester, now)
                if (next != null) {
                    val (course, date, start) = next
                    val timeText = if (date == now.toLocalDate()) {
                        PeriodTimes.format(PeriodTimes.startOf(course.startPeriod))
                    } else {
                        "${date.monthValue}/${date.dayOfMonth} " +
                            PeriodTimes.format(PeriodTimes.startOf(course.startPeriod))
                    }
                    val left = remaining(context, Duration.between(now, start).toMinutes())
                    lines.add(t.statusNextClassFmt(course.title, timeText, left))
                }
            }
        }

        AgendaStore.ensureLoaded(context)
        val events = AgendaStore.events.value
        if ("plan" in sources) {
            events.firstOrNull { it.isPlan && it.hasPreciseStart && it.hasPreciseEnd && it.isOngoing(now) }?.let {
                lines.add(t.statusOngoingPlanFmt(it.title))
            }
        }
        if ("agenda" in sources) {
            events.asSequence()
                .filter { !it.isPlan && it.startDateTime().isAfter(now) }
                .minByOrNull { it.startDateTime() }
                ?.let { ev ->
                    val d = ev.startDateTime()
                    val timeText = if (d.toLocalDate() == now.toLocalDate()) {
                        ev.startTime.ifBlank { ev.timeLabel }
                    } else {
                        "${d.monthValue}/${d.dayOfMonth}" +
                            (if (ev.startTime.isNotBlank()) " ${ev.startTime}" else "")
                    }
                    lines.add(t.statusNextAgendaFmt(ev.title, timeText))
                }
        }
        return lines
    }

    /** "25分钟后" / "1小时30分钟后"（复用小组件资源） */
    private fun remaining(context: Context, minutes: Long): String {
        if (minutes <= 1) return context.getString(R.string.widget_starting)
        val piece = when {
            minutes < 60 -> context.getString(R.string.widget_mins, minutes.toInt())
            minutes < 1440 -> context.getString(R.string.widget_hours, (minutes / 60).toInt()) +
                if (minutes % 60 > 0) context.getString(R.string.widget_mins, (minutes % 60).toInt()) else ""
            else -> context.getString(R.string.widget_days, (minutes / 1440).toInt())
        }
        return context.getString(R.string.widget_remaining, piece)
    }

    // ------------------------------------------------------------ 课程查找（与小组件一致的轻量实现）

    private fun findCurrentCourse(sem: SemesterSchedule, now: LocalDateTime): Course? {
        val date = now.toLocalDate()
        val weekNo = sem.teachingWeekOf(date)
        if (weekNo < 1 || weekNo > 40) return null
        for (c in sem.weeks[weekNo].orEmpty().filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }) {
            val start = date.atTime(PeriodTimes.startOf(c.startPeriod))
            val end = date.atTime(PeriodTimes.endOf(c.endPeriod))
            if (!now.isBefore(start) && !now.isAfter(end)) return c
        }
        return null
    }

    private fun findNextCourse(sem: SemesterSchedule, now: LocalDateTime): Triple<Course, LocalDate, LocalDateTime>? {
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val weekNo = sem.teachingWeekOf(date)
            if (weekNo < 1 || weekNo > 40) continue
            val courses = sem.weeks[weekNo].orEmpty()
                .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
                .sortedBy { it.startPeriod }
            for (c in courses) {
                val start = date.atTime(PeriodTimes.startOf(c.startPeriod))
                if (start.isAfter(now)) return Triple(c, date, start)
            }
        }
        return null
    }
}

/** 常驻通知刷新链的闹钟接收器（每次触发后重新安排下一次） */
class StatusRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                StatusNotification.refresh(appContext)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
