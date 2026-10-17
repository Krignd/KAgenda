package com.kstudio.agenda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.CourseEditStore
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.*
import com.kstudio.agenda.ui.MainActivity
import java.time.DayOfWeek
import java.time.LocalDate

object OverviewWidgetUpdater {
    data class Spec(val clazz: Class<*>, val weekly: Boolean, val coursesOnly: Boolean)
    val specs = listOf(
        Spec(TodayAgendaWidget::class.java, false, false), Spec(TodayCoursesWidget::class.java, false, true),
        Spec(WeekAgendaWidget::class.java, true, false), Spec(WeekCoursesWidget::class.java, true, true),
    )
    fun hasAny(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return specs.any { manager.getAppWidgetIds(ComponentName(context, it.clazz)).isNotEmpty() }
    }
    private data class Entry(val date: LocalDate, val title: String, val time: String, val location: String, val color: Int)
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val semester = CourseEditStore.appliedFromCache(context)
        AgendaStore.ensureLoaded(context)
        val today = LocalDate.now()
        val t = AppText.current
        for (spec in specs) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.clazz))
            if (ids.isEmpty()) continue
            val start = if (spec.weekly) today.with(DayOfWeek.MONDAY) else today
            val days = (0 until if (spec.weekly) 7 else 1).map { start.plusDays(it.toLong()) }
            val entries = days.flatMap { day ->
                val courses = semester?.coursesOnDate(day).orEmpty().map { c ->
                    Entry(day, c.title, PeriodTimes.format(PeriodTimes.startOf(c.startPeriod)), c.room, CoursePalette.colorFor(c))
                }
                val events = if (spec.coursesOnly) emptyList() else AgendaStore.events.value.filter { !it.isPlan && it.occursOn(day) }.map { e ->
                    Entry(day, e.title, e.startTime, e.location, e.displayColor.takeIf { it != 0 } ?: 0xFF1D4ED8.toInt())
                }
                (courses + events).sortedBy { FuzzyTime.sortKey(it.time) }
            }
            for (id in ids) {
                val grid = spec.weekly && spec.coursesOnly
                val rv = RemoteViews(context.packageName, if (grid) R.layout.widget_week_grid else R.layout.widget_overview)
                rv.setTextViewText(R.id.overview_title, when {
                    spec.weekly && spec.coursesOnly -> t.widgetWeekCourses
                    spec.weekly -> t.widgetWeekAgenda
                    spec.coursesOnly -> t.widgetTodayCourses
                    else -> t.widgetTodayAgenda
                })
                rv.setTextViewText(R.id.overview_date, if (spec.weekly) "$start – ${days.last()}" else today.toString())
                rv.setTextViewText(R.id.overview_more, if (entries.isEmpty()) t.widgetEmpty else t.widgetMore)
                rv.setOnClickPendingIntent(R.id.overview_root, open(context, id, today, ""))
                rv.removeAllViews(R.id.overview_rows)
                if (grid) {
                    days.forEach { day ->
                        val col = RemoteViews(context.packageName, R.layout.widget_day_column)
                        col.setTextViewText(R.id.overview_day, "${t.weekdayNarrow(day.dayOfWeek.value)}\n${day.monthValue}/${day.dayOfMonth}" + ((HolidayTable.nameOf(day)?.let(t::holidayLabel) ?: if (HolidayTable.isWorkday(day)) t.workdayMark else null)?.let { "\n$it" } ?: ""))
                        col.removeAllViews(R.id.overview_blocks)
                        entries.filter { it.date == day }.forEachIndexed { rowIndex, entry ->
                            col.addView(R.id.overview_blocks, row(context, entry, R.layout.widget_course_block, id * 10000 + day.dayOfWeek.value * 100 + rowIndex))
                        }
                        rv.addView(R.id.overview_rows, col)
                    }
                } else {
                    var previous: LocalDate? = null
                    entries.forEachIndexed { rowIndex, entry ->
                        if (spec.weekly && entry.date != previous) {
                            val header = RemoteViews(context.packageName, R.layout.widget_overview_row)
                            header.setTextViewText(R.id.overview_time, "${entry.date.monthValue}/${entry.date.dayOfMonth}")
                            header.setTextViewText(R.id.overview_name, t.weekdayShort(entry.date.dayOfWeek.value))
                            header.setTextViewText(R.id.overview_location, HolidayTable.nameOf(entry.date)?.let(t::holidayLabel) ?: if (HolidayTable.isWorkday(entry.date)) t.workdayMark else "")
                            rv.addView(R.id.overview_rows, header)
                            previous = entry.date
                        }
                        rv.addView(R.id.overview_rows, row(context, entry, R.layout.widget_overview_row, id * 10000 + rowIndex))
                    }
                }
                manager.updateAppWidget(id, rv)
            }
        }
    }
    private fun row(context: Context, entry: Entry, layout: Int, request: Int): RemoteViews = RemoteViews(context.packageName, layout).apply {
        if (layout == R.layout.widget_course_block) setInt(R.id.overview_row, "setBackgroundColor", CoursePalette.lightOf(entry.color, 36))
        setTextViewText(R.id.overview_time, entry.time)
        setTextColor(R.id.overview_time, entry.color)
        setTextViewText(R.id.overview_name, entry.title)
        setTextViewText(R.id.overview_location, entry.location)
        setOnClickPendingIntent(R.id.overview_row, open(context, request, entry.date, entry.title))
    }
    private fun open(context: Context, request: Int, date: LocalDate, title: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            data = android.net.Uri.parse("kagenda://widget/$request/${date.toEpochDay()}")
            putExtra(MainActivity.EXTRA_FOCUS_EPOCH_DAY, date.toEpochDay())
            putExtra(MainActivity.EXTRA_FOCUS_TITLE, title)
        }
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
