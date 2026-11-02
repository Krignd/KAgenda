package com.kstudio.agenda

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.ui.MainActivity
import com.kstudio.agenda.ui.theme.*
import java.io.File

/** Explicit visual fixture; no preferences, courses or import state are changed. Emulator only. */
class GlassSceneryDeviceRunner : Instrumentation() {
    private var record = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); record = arguments?.getString("record") == "true"; start() }

    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val output = File(targetContext.getExternalFilesDir(null), "spectral-tide").apply { mkdirs() }
            val phase = mutableFloatStateOf(0f)
            fun capture(name: String): Bitmap {
                waitForIdleSync()
                Thread.sleep(350)
                return uiAutomation.takeScreenshot().also { bitmap ->
                    File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            for (dark in listOf(false, true)) for (depth in listOf(0, 100, 200)) {
                runOnMainSync {
                    phase.floatValue = 0f
                    activity.setContent {
                        val configuration = Configuration(LocalConfiguration.current).apply {
                            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        }
                        CompositionLocalProvider(LocalConfiguration provides configuration) {
                            KAgendaTheme(colorDepth = depth) {
                                CompositionLocalProvider(LocalGlassScenery provides null) {
                                    Box(Modifier.fillMaxSize()) {
                                        GlassScenery(Modifier.fillMaxSize(), dark, depth / 100f, phase)
                                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 60.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                            Text("弥散光雾", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                                            Text("D I F F U S E   M I S T", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.weight(1f))
                                            FrostedSurface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text("星期二 · 10 月 20 日", style = MaterialTheme.typography.titleLarge)
                                                    Text("09:00  设计与生活", style = MaterialTheme.typography.bodyLarge)
                                                    Text("流动的色彩，安静的秩序。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge) {
                                                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    listOf("课表", "日程", "计划", "设置").forEach { Text(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Initial texture generation runs off the UI thread.
                Thread.sleep(1000)
                val first = capture("${if (dark) "dark" else "light"}-$depth-0")
                val repeat = capture("static-check")
                // Ignore status/navigation bars, which may contain a changing clock.
                var differences = 0
                for (y in first.height / 8 until first.height * 7 / 8 step 12)
                    for (x in 0 until first.width step 12)
                        if (first.getPixel(x, y) != repeat.getPixel(x, y)) differences++
                check(differences == 0) { "Static scenery changed: $differences" }
                repeat.recycle()
                runOnMainSync { phase.floatValue = 0.23f }
                val second = capture("${if (dark) "dark" else "light"}-$depth-23")
                var animated = 0
                for (y in first.height / 8 until first.height * 7 / 8 step 12)
                    for (x in 0 until first.width step 12)
                        if (first.getPixel(x, y) != second.getPixel(x, y)) animated++
                check(animated > 100) { "Phase did not change scenery: $animated" }
                runOnMainSync { phase.floatValue = 1f }
                val seam = capture("loop-check")
                var maxDelta = 0
                for (y in first.height / 8 until first.height * 7 / 8 step 12)
                    for (x in 0 until first.width step 12) {
                        val a = first.getPixel(x, y)
                        val b = seam.getPixel(x, y)
                        for (shift in listOf(0, 8, 16)) maxDelta = maxOf(maxDelta, kotlin.math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)))
                    }
                check(maxDelta <= 2) { "Visible loop seam: $maxDelta" }
                first.recycle(); second.recycle(); seam.recycle()
                // Crossing a texture boundary must not cause a full-screen brightness jump.
                runOnMainSync { phase.floatValue = 1f / 24f - 1f / 2160f }
                val beforeKey = capture("key-before")
                runOnMainSync { phase.floatValue = 1f / 24f + 1f / 2160f }
                val afterKey = capture("key-after")
                var junctionDelta = 0
                for (y in beforeKey.height / 8 until beforeKey.height * 7 / 8 step 12)
                    for (x in 0 until beforeKey.width step 12) {
                        val a = beforeKey.getPixel(x, y)
                        val b = afterKey.getPixel(x, y)
                        for (shift in listOf(0, 8, 16)) junctionDelta = maxOf(junctionDelta,
                            kotlin.math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)))
                    }
                check(junctionDelta <= 3) { "Texture boundary flashes: $junctionDelta" }
                beforeKey.recycle(); afterKey.recycle()
            }
            runOnMainSync {
                activity.setContent {
                    KAgendaTheme(colorDepth = 100) {
                        GlassBackdrop(animateBackground = true) {
                            Column(Modifier.padding(28.dp, 80.dp)) {
                                FrostedSurface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge) {
                                    Text("弥散光雾 · 实时玻璃", Modifier.padding(28.dp), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
            val liveStart = capture("live-start")
            val recording = if (record) uiAutomation.executeShellCommand(
                "screenrecord --bit-rate 4000000 --time-limit 8 ${File(output, "spectral-tide-live.mp4").absolutePath}",
            ) else null
            Thread.sleep(if (record) 9000 else 3000)
            recording?.close()
            val liveEnd = capture("live-end")
            var liveChanges = 0
            for (y in liveStart.height / 4 until liveStart.height * 3 / 4 step 12)
                for (x in 0 until liveStart.width step 12)
                    if (liveStart.getPixel(x, y) != liveEnd.getPixel(x, y)) liveChanges++
            check(liveChanges > 100) { "Live backdrop did not animate: $liveChanges" }
            liveStart.recycle(); liveEnd.recycle()
            runOnMainSync { activity.finish() }
            result.putString("stream", "PASS: light/dark × 0/100/200 depth; fixed dither stability, phase change, seamless loop and texture boundary continuity; live backdrop captured.\n")
            finish(-1, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
            finish(0, result)
        }
    }
}
