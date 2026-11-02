package com.kstudio.agenda

import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.TextView
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.UI_STYLE_DEFAULT
import com.kstudio.agenda.data.UI_STYLE_GLASS
import com.kstudio.agenda.model.*
import com.kstudio.agenda.widget.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Real RemoteViews inflation for all eight providers; invoked explicitly on an emulator. */
internal object AppearanceDeviceChecks {
    fun run(test: Instrumentation): String = runBlocking {
        val context = test.targetContext
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
        val original = SettingsStore.read(context)
        val host = AppWidgetHost(context, 33718)
        val manager = AppWidgetManager.getInstance(context)
        val providers = NextClassWidgetUpdater.SPECS.map { it.clazz } + OverviewWidgetUpdater.specs.map { it.clazz }
        test.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            val ids = providers.map { provider ->
                val id = host.allocateAppWidgetId()
                check(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider))) { "Widget bind failed: ${provider.simpleName}" }
                id
            }
            host.startListening()
            for ((style, depth) in listOf(UI_STYLE_DEFAULT to 0, UI_STYLE_GLASS to 0, UI_STYLE_GLASS to 100, UI_STYLE_GLASS to 200)) {
                SettingsStore.setUiStyle(context, style)
                SettingsStore.setGlassColorDepth(context, depth)
                NextClassWidgetUpdater.updateAll(context)
                for (id in ids) {
                    var view: AppWidgetHostView? = null
                    test.runOnMainSync { view = host.createView(context, id, manager.getAppWidgetInfo(id)) }
                    var loaded = false
                    repeat(30) {
                        test.runOnMainSync {
                            loaded = view?.findViewById<TextView>(R.id.widget_name) != null || view?.findViewById<TextView>(R.id.overview_title) != null
                        }
                        if (!loaded) Thread.sleep(50)
                    }
                    check(loaded) { "RemoteViews did not inflate: $id / $style / $depth" }
                    var renderFailure: Throwable? = null
                    test.runOnMainSync { runCatching {
                        val root = view?.findViewById<android.view.View>(R.id.widget_root)
                            ?: view?.findViewById(R.id.overview_root)
                        if (style == UI_STYLE_GLASS) {
                            val layers = root?.background as? android.graphics.drawable.LayerDrawable
                            check(layers != null && layers.numberOfLayers == 4)
                            val colors = (layers.getDrawable(0) as android.graphics.drawable.GradientDrawable).colors!!
                            check((colors[0] ushr 24) == 248 - depth * 7 / 100)
                            val dark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                            val iconHues = listOf(187f, 219f, 253f)
                            for (index in colors.indices) {
                                val hsl = FloatArray(3)
                                androidx.core.graphics.ColorUtils.colorToHSL(colors[index], hsl)
                                check(kotlin.math.abs(hsl[0] - iconHues[index]) < 18f) { "Widget hue differs from launcher icon" }
                                if (!dark) check(hsl[2] > 0.80f) { "Widget color must remain pastel" }
                            }
                        } else check(root?.background is android.graphics.drawable.GradientDrawable)
                        if (id == ids[1]) {
                            val bitmap = android.graphics.Bitmap.createBitmap(600, 600, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            val secondary = view!!.findViewById<TextView>(R.id.widget_info).currentTextColor
                            val accent = view!!.findViewById<TextView>(R.id.widget_remaining).currentTextColor
                            for (wallpaper in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)) {
                                canvas.drawColor(wallpaper)
                                root!!.background.setBounds(0, 0, 600, 600)
                                root.background.draw(canvas)
                                for (x in listOf(120, 300, 480)) for (y in listOf(120, 300, 480)) {
                                    val background = bitmap.getPixel(x, y)
                                    check(androidx.core.graphics.ColorUtils.calculateContrast(secondary, background) >= 4.5) { "Widget secondary contrast: $depth/$style/${androidx.core.graphics.ColorUtils.calculateContrast(secondary, background)}" }
                                    check(androidx.core.graphics.ColorUtils.calculateContrast(accent, background) >= 4.5) { "Widget accent contrast: $depth/$style/${androidx.core.graphics.ColorUtils.calculateContrast(accent, background)}" }
                                }
                            }
                            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                            view!!.measure(android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY))
                            view!!.layout(0, 0, 600, 600)
                            view!!.draw(canvas)
                            val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                            java.io.File(context.getExternalFilesDir(null), "widget-refinement6-$mode-$style-$depth.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                        }
                    }.onFailure { renderFailure = it } }
                    renderFailure?.let { throw it }
                }
                for (spec in OverviewWidgetUpdater.specs) {
                    val factory = OverviewWidgetService.Factory(context, spec.weekly, spec.coursesOnly)
                    factory.onCreate()
                    for (position in 0 until factory.count) test.runOnMainSync {
                        val row = factory.getViewAt(position).apply(context, android.widget.FrameLayout(context))
                        check(row != null)
                    }
                    factory.onDestroy()
                }
            }
            for (speed in listOf(25, 100, 400)) {
                SettingsStore.setMotionSpeed(context, speed)
                check(SettingsStore.read(context).motionSpeedPercent == speed)
            }
            val date = LocalDate.parse("2026-10-19")
            SettingsStore.setHolidayOverride(context, date, HolidayOverride(date, HolidayKind.HOLIDAY, "校庆"))
            val decoded = HolidayOverrides.decode(SettingsStore.read(context).holidayOverridesRaw)
            check(decoded[date.toEpochDay()]?.name == "校庆")
            SettingsStore.setHolidayOverride(context, date, null)
            check(!HolidayOverrides.decode(SettingsStore.read(context).holidayOverridesRaw).containsKey(date.toEpochDay()))
            "PASS: 8 widgets × 4 appearances + list/grid rows inflated; denser glass opacity and paler launcher-icon gradient checked; speed and holiday overrides persisted/restored.\n"
        } finally {
            host.stopListening()
            host.deleteHost()
            test.uiAutomation.dropShellPermissionIdentity()
            SettingsStore.setUiStyle(context, original.uiStyle)
            SettingsStore.setGlassColorDepth(context, original.glassColorDepth)
            SettingsStore.setMotionSpeed(context, original.motionSpeedPercent)
            // Restore any pre-existing mark changed by this test.
            val date = LocalDate.parse("2026-10-19")
            SettingsStore.setHolidayOverride(context, date, HolidayOverrides.decode(original.holidayOverridesRaw)[date.toEpochDay()])
        }
    }
}
