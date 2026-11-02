package com.kstudio.agenda.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kstudio.agenda.R
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.model.CoursePalette
import com.kstudio.agenda.model.HolidayTable
import com.kstudio.agenda.ui.MainActivity
import java.time.LocalDate

/** ListView is a supported RemoteViews collection; ScrollView is rejected by launchers. */
class OverviewWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext,
        intent.getBooleanExtra("weekly", false), intent.getBooleanExtra("coursesOnly", false))

    internal class Factory(val context: Context, val weekly: Boolean, val coursesOnly: Boolean) : RemoteViewsFactory {
        private val grid = weekly && coursesOnly
        private var days = emptyList<LocalDate>()
        private var rows = emptyList<OverviewWidgetUpdater.Entry>()
        private var columns = emptyList<List<OverviewWidgetUpdater.Entry>>()
        private var appearance: WidgetAppearance? = null
        override fun onCreate() = onDataSetChanged()
        override fun onDataSetChanged() {
            appearance = WidgetAppearance(context)
            val data = OverviewWidgetUpdater.content(context, weekly, coursesOnly)
            days = data.days
            columns = days.map { date -> data.entries.filter { it.date == date } }
            rows = if (weekly && !grid) columns.flatMapIndexed { index, entries ->
                if (entries.isEmpty()) entries else listOf(OverviewWidgetUpdater.Entry(days[index],
                    AppText.current.weekdayShort(days[index].dayOfWeek.value), "${days[index].monthValue}/${days[index].dayOfMonth}",
                    mark(days[index]), 0, header = true)) + entries
            } else data.entries
        }
        override fun onDestroy() { rows = emptyList(); columns = emptyList(); appearance = null }
        override fun getCount(): Int = if (grid) 1 + (columns.maxOfOrNull { it.size } ?: 0) else rows.size
        override fun getViewAt(position: Int): RemoteViews {
            if (!grid) return row(rows[position], R.layout.widget_overview_row)
            return RemoteViews(context.packageName, R.layout.widget_week_row).apply {
                for ((index, date) in days.withIndex()) {
                    val column = RemoteViews(context.packageName, R.layout.widget_day_column)
                    column.removeAllViews(R.id.overview_blocks)
                    if (position == 0) {
                        column.setTextViewText(R.id.overview_day, "${AppText.current.weekdayNarrow(date.dayOfWeek.value)}\n${date.monthValue}/${date.dayOfMonth}\n${mark(date)}")
                        appearance?.day(column)
                    } else {
                        column.setViewVisibility(R.id.overview_day, View.GONE)
                        columns.getOrNull(index)?.getOrNull(position - 1)?.let {
                            column.addView(R.id.overview_blocks, row(it, R.layout.widget_course_block))
                        }
                    }
                    addView(R.id.overview_week_columns, column)
                }
            }
        }
        private fun mark(date: LocalDate): String = HolidayTable.nameOf(date)?.let(AppText.current::holidayLabel)
            ?: if (HolidayTable.isWorkday(date)) AppText.current.workdayMark else ""
        private fun row(entry: OverviewWidgetUpdater.Entry, layout: Int) = RemoteViews(context.packageName, layout).apply {
            if (layout == R.layout.widget_course_block) setInt(R.id.overview_row, "setBackgroundColor", CoursePalette.lightOf(entry.color, 36))
            setTextViewText(R.id.overview_time, entry.time)
            setTextViewText(R.id.overview_name, entry.title)
            setTextViewText(R.id.overview_location, entry.location)
            appearance?.row(this, entry.color.takeUnless { entry.header })
            setOnClickFillInIntent(R.id.overview_row, Intent().apply {
                putExtra(MainActivity.EXTRA_FOCUS_EPOCH_DAY, entry.date.toEpochDay())
                putExtra(MainActivity.EXTRA_FOCUS_TITLE, if (entry.header) "" else entry.title)
            })
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 2
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}
