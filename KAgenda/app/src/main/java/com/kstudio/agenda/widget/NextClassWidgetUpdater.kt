package com.kstudio.agenda.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.ScheduleCache
import com.kstudio.agenda.model.AgendaEvent
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.PeriodTimes
import com.kstudio.agenda.model.SemesterSchedule
import com.kstudio.agenda.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 小组件规格：四种尺寸入口（2×1 / 2×2 / 4×1 / 4×2），共用同一套刷新逻辑 */
data class WidgetSpec(
    val clazz: Class<*>,
    val layoutRes: Int,
    val showTitle: Boolean,
    val showNext2: Boolean,
    /** 信息行是否压缩（2×1 空间小：上课中时只显示“至+下节”简讯） */
    val compactInfo: Boolean = false,
)

/**
 * 「下一节课」小组件更新器：
 * - 从本地课表缓存计算下一节课（课程名 / 时间 / 地点 / 剩余时间）；
 * - 更新所有尺寸的全部小组件实例；
 * - 维护“定时刷新链”（即将上课前 1 分钟粒度，空闲时降频），随组件存在与否自动启停。
 */
object NextClassWidgetUpdater {

    val SPECS = listOf(
        WidgetSpec(NextClassWidget2x1::class.java, R.layout.widget_class_2x1, showTitle = false, showNext2 = false, compactInfo = true),
        WidgetSpec(NextClassWidget2x2::class.java, R.layout.widget_class_2x2, showTitle = true, showNext2 = true),
        WidgetSpec(NextClassWidget4x1::class.java, R.layout.widget_class_4x1, showTitle = false, showNext2 = false),
        WidgetSpec(NextClassWidget4x2::class.java, R.layout.widget_class_4x2, showTitle = true, showNext2 = true),
    )

    private const val REFRESH_REQUEST_CODE = 925100
    private const val ACCENT_DEFAULT = 0xFF1D4ED8.toInt()

    private data class Upcoming(val course: Course, val date: LocalDate, val start: LocalDateTime) {
        /** 本节课程的结束时刻 */
        fun end(): LocalDateTime = date.atTime(PeriodTimes.endOf(course.endPeriod))
    }

    // ------------------------------------------------------------ 对外接口

    /** 更新全部小组件并安排下一次刷新（应用启动 / 同步完成 / 提醒触发 / 开机时调用） */
    fun updateAndSchedule(context: Context) {
        updateAll(context)
        scheduleNext(context)
    }

    /** 仅更新全部小组件 */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val semester = ScheduleCache.load(context)
        AgendaStore.ensureLoaded(context)
        val events = AgendaStore.events.value
        val now = LocalDateTime.now()
        val current = semester?.let { findCurrent(it, now) }
        val upcoming = semester?.let { findUpcoming(it, now, 2) }.orEmpty()
        for (spec in SPECS) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.clazz))
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    buildViews(context, spec, semester, current, upcoming, events, now, id),
                )
            }
        }
    }

    /** 是否存在任意尺寸的小组件 */
    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        for (spec in SPECS) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.clazz))
            if (ids.isNotEmpty()) return true
        }
        return false
    }

    /**
     * 安排下一次刷新（刷新链，保证“时间文案”及时）：
     * - 正在上课：每分钟（倒计时到下课）；
     * - 距上课 ≤1 小时：每分钟；≤3 小时：每 5 分钟；≤24 小时：每 30 分钟；更远：每 6 小时；
     * - 无课程：每 6 小时兜底。
     * 已授权精确闹钟时用精确闹钟（更准时）；否则降级 setAndAllowWhileIdle。
     * 无小组件时自动停止刷新链。
     */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = refreshPendingIntent(context)
        am.cancel(pi)
        if (!hasAnyWidget(context)) return

        val semester = ScheduleCache.load(context)
        AgendaStore.ensureLoaded(context)
        val events = AgendaStore.events.value
        val now = LocalDateTime.now()
        val current = semester?.let { findCurrent(it, now) }
        val upcoming = semester?.let { findUpcoming(it, now, 1) }.orEmpty()
        val agendaOngoing = events.firstOrNull { it.isOngoing(now) }
        val agendaNext = events.filter { it.startDateTime().isAfter(now) }
            .minByOrNull { it.startDateTime() }
        val nextStarts = listOfNotNull(
            upcoming.firstOrNull()?.start,
            agendaNext?.startDateTime(),
        )
        val delayMs = when {
            current != null || agendaOngoing != null -> 60_000L
            nextStarts.isEmpty() -> 6 * 60 * 60_000L
            else -> {
                val delta = Duration.between(now, nextStarts.minOrNull()!!).toMillis()
                when {
                    delta <= 60 * 60_000L -> 60_000L
                    delta <= 3 * 60 * 60_000L -> 5 * 60_000L
                    delta <= 24 * 60 * 60_000L -> 30 * 60_000L
                    else -> 6 * 60 * 60_000L
                }
            }
        }
        val triggerAt = System.currentTimeMillis() + delayMs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ------------------------------------------------------------ 视图构建

    private fun buildViews(
        context: Context,
        spec: WidgetSpec,
        semester: SemesterSchedule?,
        current: Upcoming?,
        upcoming: List<Upcoming>,
        events: List<AgendaEvent>,
        now: LocalDateTime,
        widgetId: Int,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, spec.layoutRes)

        // 自建日程（含计划）：进行中 / 下一项（需先算好，用于点击定位）
        val agendaOngoing = events.firstOrNull { it.isOngoing(now) }
        val agendaNext = events
            .filter { it.startDateTime().isAfter(now) }
            .minByOrNull { it.startDateTime() }

        // 点击小组件：打开应用并定位到“当前展示项”的日/周/月，同时闪烁对应课程/日期
        val focusTarget: Pair<LocalDate, String> = when {
            current != null -> current.date to current.course.title
            agendaOngoing != null -> LocalDate.ofEpochDay(agendaOngoing.dateEpochDay) to agendaOngoing.title
            upcoming.firstOrNull() != null -> upcoming.first().date to upcoming.first().course.title
            agendaNext != null -> agendaNext.startDateTime().toLocalDate() to agendaNext.title
            else -> now.toLocalDate() to ""
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_FOCUS_EPOCH_DAY, focusTarget.first.toEpochDay())
            putExtra(MainActivity.EXTRA_FOCUS_TITLE, focusTarget.second)
        }
        val openPi = PendingIntent.getActivity(
            context,
            widgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.widget_root, openPi)

        rv.setViewVisibility(R.id.widget_title, if (spec.showTitle) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_next2, if (spec.showNext2) View.VISIBLE else View.GONE)

        if (semester == null && events.isEmpty()) {
            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_next_class))
            rv.setTextViewText(R.id.widget_name, context.getString(R.string.widget_no_data))
            rv.setTextViewText(R.id.widget_info, "")
            rv.setTextViewText(R.id.widget_remaining, "")
            rv.setTextViewText(R.id.widget_next2, "")
            rv.setTextColor(R.id.widget_remaining, ACCENT_DEFAULT)
            return rv
        }

        // ---------- 三态之一：正在上课（当前课程 + 下课倒计时 + 下一节课） ----------
        if (current != null) {
            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_in_class))
            rv.setTextViewText(R.id.widget_name, current.course.title)
            val endLabel = context.getString(
                R.string.widget_until,
                PeriodTimes.format(PeriodTimes.endOf(current.course.endPeriod)),
            )
            val next = upcoming.firstOrNull()
            val nextLabel = next?.let {
                context.getString(
                    R.string.widget_next_class_line,
                    PeriodTimes.format(PeriodTimes.startOf(it.course.startPeriod)),
                    it.course.title,
                )
            }
            val info = if (spec.showNext2) {
                // 大尺寸：信息行放“结束时间 + 老师 + 地点”，下一节单独一行
                listOfNotNull(
                    endLabel,
                    current.course.teacher.ifBlank { null },
                    current.course.room.ifBlank { null },
                ).joinToString(" · ")
            } else if (spec.compactInfo) {
                // 2×1 极小尺寸：至 + 老师
                listOfNotNull(endLabel, current.course.teacher.ifBlank { null }).joinToString(" · ")
            } else {
                // 中等尺寸：至 + 老师 + 下节
                listOfNotNull(
                    endLabel,
                    current.course.teacher.ifBlank { null },
                    nextLabel,
                ).joinToString(" · ")
            }
            rv.setTextViewText(R.id.widget_info, info)
            val remainMinutes = Duration.between(now, current.end()).toMinutes().coerceAtLeast(1)
            rv.setTextViewText(
                R.id.widget_remaining,
                context.getString(R.string.widget_until_end, durationPiece(context, remainMinutes)),
            )
            rv.setTextColor(R.id.widget_remaining, CoursePalette.colorFor(current.course))
            rv.setTextViewText(R.id.widget_next2, if (spec.showNext2) (nextLabel ?: "") else "")
            return rv
        }

        // ---------- 三态之一（备选）：进行中的自建日程 ----------
        if (agendaOngoing != null) {
            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_ongoing))
            rv.setTextViewText(R.id.widget_name, agendaOngoing.title)
            val info = listOfNotNull(
                agendaOngoing.rangeLabel.ifBlank { null },
                agendaOngoing.location.ifBlank { null },
            ).joinToString(" · ")
            rv.setTextViewText(R.id.widget_info, info)
            val left = Duration.between(now, agendaOngoing.endDateTime()).toMinutes()
            rv.setTextViewText(
                R.id.widget_remaining,
                if (left > 0) context.getString(R.string.widget_until_end, durationPiece(context, left)) else "",
            )
            rv.setTextColor(R.id.widget_remaining, ACCENT_DEFAULT)
            rv.setTextViewText(
                R.id.widget_next2,
                if (spec.showNext2) nextItemLabel(context, upcoming, agendaNext) else "",
            )
            return rv
        }

        if (upcoming.isEmpty() && agendaNext == null) {
            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_next_class))
            rv.setTextViewText(R.id.widget_name, context.getString(R.string.widget_none))
            rv.setTextViewText(R.id.widget_info, "")
            rv.setTextViewText(R.id.widget_remaining, "")
            rv.setTextViewText(R.id.widget_next2, "")
            rv.setTextColor(R.id.widget_remaining, ACCENT_DEFAULT)
            return rv
        }

        // ---------- 三态之二：下一项（课程与自建日程取更早者） ----------
        val nextCourse = upcoming.firstOrNull()
        val agendaEarlier = agendaNext != null &&
            (nextCourse == null || agendaNext.startDateTime().isBefore(nextCourse.start))
        if (agendaEarlier) {
            val ev = agendaNext!!
            rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_next_agenda))
            rv.setTextViewText(R.id.widget_name, ev.title)
            rv.setTextViewText(
                R.id.widget_info,
                listOfNotNull(agendaTimeLabel(context, ev), ev.location.ifBlank { null }).joinToString(" · "),
            )
            val minutes = Duration.between(now, ev.startDateTime()).toMinutes().coerceAtLeast(0)
            rv.setTextViewText(
                R.id.widget_remaining,
                if (ev.startTime.isNotBlank()) remainingText(context, minutes) else "",
            )
            rv.setTextColor(R.id.widget_remaining, ACCENT_DEFAULT)
            rv.setTextViewText(
                R.id.widget_next2,
                if (spec.showNext2) nextItemLabel(context, upcoming, null) else "",
            )
            return rv
        }

        // ---------- 三态之二（课程）：下一节课（含明天 / 后天 / 日期标注） ----------
        rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_next_class))
        val first = nextCourse!!
        rv.setTextViewText(R.id.widget_name, first.course.title)
        val info = if (spec.compactInfo) {
            // 2×1：时间 + 老师
            listOfNotNull(
                timeLabel(context, first),
                first.course.teacher.ifBlank { null },
            ).joinToString(" · ")
        } else {
            // 其他尺寸：时间 + 老师 + 地点
            listOfNotNull(
                timeLabel(context, first),
                first.course.teacher.ifBlank { null },
                first.course.room.ifBlank { null },
            ).joinToString(" · ")
        }
        rv.setTextViewText(R.id.widget_info, info)
        val minutes = Duration.between(now, first.start).toMinutes().coerceAtLeast(0)
        rv.setTextViewText(R.id.widget_remaining, remainingText(context, minutes))
        rv.setTextColor(R.id.widget_remaining, CoursePalette.colorFor(first.course))

        if (spec.showNext2) {
            val second = upcoming.getOrNull(1)
            rv.setTextViewText(
                R.id.widget_next2,
                if (second == null) "" else context.getString(
                    R.string.widget_next2,
                    PeriodTimes.format(PeriodTimes.startOf(second.course.startPeriod)),
                    second.course.title,
                ),
            )
        } else {
            rv.setTextViewText(R.id.widget_next2, "")
        }
        return rv
    }

    /** 日程时间文案：今天 "08:00"，明天 "明天 08:00"，更远 "9/24 08:00"（无具体时刻时为日期/今天） */
    private fun agendaTimeLabel(context: Context, ev: AgendaEvent): String {
        val start = ev.startDateTime()
        val today = LocalDate.now()
        val hm = ev.startTime
        return when (start.toLocalDate()) {
            today -> if (hm.isNotBlank()) hm else context.getString(R.string.widget_today)
            today.plusDays(1) -> context.getString(R.string.widget_tomorrow) +
                if (hm.isNotBlank()) " $hm" else ""
            else -> "${start.monthValue}/${start.dayOfMonth}" + (if (hm.isNotBlank()) " $hm" else "")
        }
    }

    /** 下一项附加行：展示候选池中更早的下一个（课程优先） */
    private fun nextItemLabel(context: Context, upcoming: List<Upcoming>, agendaNext: AgendaEvent?): String =
        when {
            upcoming.isNotEmpty() -> context.getString(
                R.string.widget_next2,
                PeriodTimes.format(PeriodTimes.startOf(upcoming[0].course.startPeriod)),
                upcoming[0].course.title,
            )
            agendaNext != null -> context.getString(
                R.string.widget_next2,
                agendaTimeLabel(context, agendaNext),
                agendaNext.title,
            )
            else -> ""
        }

    /** 时间文案：今天 "08:00-09:35"；明天/后天 "明天 08:00-09:35"；更远 "9/24 周三 08:00"（自动本地化） */
    private fun timeLabel(context: Context, u: Upcoming): String {
        val start = PeriodTimes.format(PeriodTimes.startOf(u.course.startPeriod))
        val end = PeriodTimes.format(PeriodTimes.endOf(u.course.endPeriod))
        val range = "$start-$end"
        val today = LocalDate.now()
        return when (u.date) {
            today -> range
            today.plusDays(1) -> context.getString(R.string.widget_tomorrow) + " " + range
            today.plusDays(2) -> context.getString(R.string.widget_day_after) + " " + range
            else -> DateTimeFormatter.ofPattern("M/d EEE HH:mm", Locale.getDefault()).format(u.start)
        }
    }

    /** "25分钟后" / "2小时15分后" / "1天3小时后" / "即将开始"（按当前语言资源本地化） */
    private fun remainingText(context: Context, minutes: Long): String {
        if (minutes <= 1) return context.getString(R.string.widget_starting)
        return context.getString(R.string.widget_remaining, durationPiece(context, minutes))
    }

    /** 时长片段："25分钟" / "2小时15分钟" / "1天3小时" */
    private fun durationPiece(context: Context, minutes: Long): String {
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> context.getString(R.string.widget_days, days.toInt()) +
                if (hours > 0) context.getString(R.string.widget_hours, hours.toInt()) else ""
            hours > 0 -> context.getString(R.string.widget_hours, hours.toInt()) +
                if (mins > 0) context.getString(R.string.widget_mins, mins.toInt()) else ""
            else -> context.getString(R.string.widget_mins, mins.toInt())
        }
    }

    // ------------------------------------------------------------ 当前 / 下一节课

    /** 正在进行的课程（仅当天，按节次时间判断） */
    private fun findCurrent(sem: SemesterSchedule, now: LocalDateTime): Upcoming? {
        val date = now.toLocalDate()
        val weekNo = sem.teachingWeekOf(date)
        if (weekNo < 1 || weekNo > 40) return null
        val courses = sem.weeks[weekNo].orEmpty()
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
        for (c in courses) {
            val start = date.atTime(PeriodTimes.startOf(c.startPeriod))
            val end = date.atTime(PeriodTimes.endOf(c.endPeriod))
            if (!now.isBefore(start) && !now.isAfter(end)) return Upcoming(c, date, start)
        }
        return null
    }

    /** 从当前时刻起，向后最多扫描 7 天，取前 limit 节“尚未开始”的课 */
    private fun findUpcoming(sem: SemesterSchedule, now: LocalDateTime, limit: Int): List<Upcoming> {
        val out = mutableListOf<Upcoming>()
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val weekNo = sem.teachingWeekOf(date)
            if (weekNo < 1 || weekNo > 40) continue
            val courses = sem.weeks[weekNo].orEmpty()
                .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursInWeek(weekNo) }
                .sortedBy { it.startPeriod }
            for (c in courses) {
                val start = date.atTime(PeriodTimes.startOf(c.startPeriod))
                if (start.isAfter(now)) {
                    out.add(Upcoming(c, date, start))
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }
}
