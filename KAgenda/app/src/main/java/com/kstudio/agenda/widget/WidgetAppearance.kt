package com.kstudio.agenda.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.kstudio.agenda.R
import com.kstudio.agenda.data.SettingsStore
import kotlinx.coroutines.runBlocking

/** RemoteViews cannot sample/blur the launcher wallpaper; use the same milky optical palette. */
internal class WidgetAppearance(context: Context) {
    private val settings = runBlocking { SettingsStore.read(context) }
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val primary = if (dark) 0xFFE6ECF6.toInt() else 0xFF0F172A.toInt()
    private val secondary = if (dark) 0xFFE0EAF8.toInt() else 0xFF1E293B.toInt()
    // Bound the diffused glows and white/black wallpaper showing through the denser coating.
    private val backing = if (dark) ColorUtils.blendARGB(0xFF365473.toInt(), 0xFF466D93.toInt(), settings.glassColorDepth / 200f)
        else ColorUtils.blendARGB(0xFFB4C6E5.toInt(), 0xFF91A9D3.toInt(), settings.glassColorDepth / 200f)
    private val background = if (!settings.glassUi) R.drawable.widget_bg else when ((settings.glassColorDepth + 12) / 25) {
        0 -> R.drawable.widget_glass_0
        1 -> R.drawable.widget_glass_25
        2 -> R.drawable.widget_glass_50
        3 -> R.drawable.widget_glass_75
        4 -> R.drawable.widget_glass_100
        5 -> R.drawable.widget_glass_125
        6 -> R.drawable.widget_glass_150
        7 -> R.drawable.widget_glass_175
        else -> R.drawable.widget_glass_200
    }
    fun accent(color: Int): Int {
        if (ColorUtils.calculateContrast(color or 0xFF000000.toInt(), backing) >= 4.5) return color
        val target = if (dark) 0xFFEFF6FF.toInt() else 0xFF14284A.toInt()
        return (1..20).asSequence().map { ColorUtils.blendARGB(color, target, it / 20f) }
            .firstOrNull { ColorUtils.calculateContrast(it, backing) >= 4.5 } ?: target
    }
    fun next(rv: RemoteViews, spec: WidgetSpec) {
        rv.setInt(R.id.widget_root, "setBackgroundResource", background)
        rv.setTextColor(R.id.widget_name, primary)
        for (id in listOf(R.id.widget_title, R.id.widget_info, R.id.widget_room, R.id.widget_next2)) rv.setTextColor(id, secondary)
        if (!spec.compactInfo) rv.setTextColor(R.id.widget_teacher, secondary)
    }
    fun overview(rv: RemoteViews) {
        rv.setInt(R.id.overview_root, "setBackgroundResource", background)
        rv.setTextColor(R.id.overview_title, primary)
        rv.setTextColor(R.id.overview_date, secondary)
        rv.setTextColor(R.id.overview_more, secondary)
    }
    fun row(rv: RemoteViews, color: Int? = null) {
        rv.setTextColor(R.id.overview_name, primary)
        rv.setTextColor(R.id.overview_location, secondary)
        rv.setTextColor(R.id.overview_time, color?.let(::accent) ?: secondary)
    }
    fun day(rv: RemoteViews) { rv.setTextColor(R.id.overview_day, secondary) }
}
