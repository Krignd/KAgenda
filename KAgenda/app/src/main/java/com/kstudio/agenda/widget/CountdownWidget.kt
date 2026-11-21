package com.kstudio.agenda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.CourseEditStore
import com.kstudio.agenda.data.Deadline
import com.kstudio.agenda.data.PlannerStore
import com.kstudio.agenda.data.countdowns
import com.kstudio.agenda.model.Schools
import com.kstudio.agenda.ui.MainActivity
import java.time.Duration
import java.time.LocalDateTime

class CountdownWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NextClassWidgetUpdater.updateAndSchedule(context)
    }

    override fun onDisabled(context: Context) = NextClassWidgetUpdater.scheduleNext(context)

    companion object {
        private fun ids(context: Context) = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, CountdownWidget::class.java))

        internal fun exists(context: Context) = ids(context).isNotEmpty()

        internal fun nearest(context: Context, now: LocalDateTime): Deadline? {
            AgendaStore.ensureLoaded(context)
            val semester = CourseEditStore.appliedFromCache(context)
            return PlannerStore.read(context).countdowns(AgendaStore.events.value, semester, Schools.currentId)
                .filter { it.at > now }.minByOrNull { it.at }
        }

        internal fun refresh(context: Context) {
            val instances = ids(context)
            if (instances.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val now = LocalDateTime.now()
            val next = nearest(context, now)
            instances.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_countdown)
                views.setTextViewText(R.id.countdown_title, next?.title ?: context.getString(R.string.planner_empty_widget))
                views.setViewVisibility(R.id.countdown_clock, if (next == null) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.countdown_due, next?.at?.toString()?.replace('T', ' ').orEmpty())
                views.setChronometerCountDown(R.id.countdown_clock, true)
                views.setChronometer(R.id.countdown_clock,
                    SystemClock.elapsedRealtime() + (next?.let { Duration.between(now, it.at).toMillis() } ?: 0), null, next != null)
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    data = android.net.Uri.parse("kagenda://countdown-widget/$id")
                    putExtra(MainActivity.EXTRA_WIDGET_KIND, "countdown")
                }
                views.setOnClickPendingIntent(R.id.countdown_widget_root,
                    PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
