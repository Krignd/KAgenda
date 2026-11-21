package com.kstudio.agenda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    internal data class Entry(val date: LocalDate, val title: String, val time: String, val location: String, val color: Int, val header: Boolean = false)
    internal data class Content(val days: List<LocalDate>, val entries: List<Entry>)
    fun hasAny(context: Context): Boolean = specs.any {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it.clazz)).isNotEmpty()
    }
    internal fun content(context: Context, weekly: Boolean, coursesOnly: Boolean): Content {
        val semester = CourseEditStore.appliedFromCache(context)
        AgendaStore.ensureLoaded(context)
        val today = LocalDate.now()
        val start = if (weekly) today.with(DayOfWeek.MONDAY) else today
        val days = (0 until if (weekly) 7 else 1).map { start.plusDays(it.toLong()) }
        return Content(days, days.flatMap { day ->
            val courses = semester?.coursesOnDate(day).orEmpty().map { c ->
                Entry(day, c.title, PeriodTimes.format(c.startTime), c.room, CoursePalette.colorFor(c))
            }
            val events = if (coursesOnly) emptyList() else AgendaStore.events.value.filter { !it.isPlan && it.occursOn(day) }.map { e ->
                Entry(day, e.title, e.startTime, e.location, e.displayColor.takeIf { it != 0 } ?: 0xFF1D4ED8.toInt())
            }
            (courses + events).sortedBy { FuzzyTime.sortKey(it.time) }
        })
    }
    @Suppress("DEPRECATION")
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val appearance by lazy { WidgetAppearance(context) }
        val t = AppText.current
        for (spec in specs) {
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.clazz))
            if (ids.isEmpty()) continue
            val snapshot = content(context, spec.weekly, spec.coursesOnly)
            for (id in ids) {
                val rv = RemoteViews(context.packageName, if (spec.weekly && spec.coursesOnly) R.layout.widget_week_grid else R.layout.widget_overview)
                appearance.overview(rv)
                rv.setTextViewText(R.id.overview_title, when {
                    spec.weekly && spec.coursesOnly -> t.widgetWeekCourses
                    spec.weekly -> t.widgetWeekAgenda
                    spec.coursesOnly -> t.widgetTodayCourses
                    else -> t.widgetTodayAgenda
                })
                rv.setTextViewText(R.id.overview_date, if (spec.weekly) "${snapshot.days.first()} – ${snapshot.days.last()}" else snapshot.days.first().toString())
                rv.setTextViewText(R.id.overview_more, if (snapshot.entries.isEmpty()) t.widgetEmpty else t.widgetMore)
                rv.setOnClickPendingIntent(R.id.overview_root, PendingIntent.getActivity(context, id, openIntent(context, id).apply {
                    putExtra(MainActivity.EXTRA_FOCUS_EPOCH_DAY, LocalDate.now().toEpochDay())
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                // The template has an explicit activity component; rows supply date/title only.
                rv.setPendingIntentTemplate(R.id.overview_rows, PendingIntent.getActivity(context, id + 500_000,
                    openIntent(context, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
                rv.setRemoteAdapter(R.id.overview_rows, Intent(context, OverviewWidgetService::class.java).apply {
                    putExtra("weekly", spec.weekly)
                    putExtra("coursesOnly", spec.coursesOnly)
                    data = Uri.parse("kagenda://widget-list/$id")
                })
                manager.updateAppWidget(id, rv)
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.overview_rows)
        }
    }
    private fun openIntent(context: Context, id: Int) = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        data = Uri.parse("kagenda://widget/$id")
        putExtra(MainActivity.EXTRA_WIDGET_KIND, "overview")
    }
}
