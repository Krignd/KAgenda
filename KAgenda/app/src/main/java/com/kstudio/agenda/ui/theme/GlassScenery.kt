package com.kstudio.agenda.ui.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.Half
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Diffuse, edgeless color mist. All field calculations happen once on a worker thread. */
@Composable
internal fun GlassScenery(modifier: Modifier, dark: Boolean, depth: Float, phase: State<Float>?) {
    val safeDepth = depth.coerceIn(0f, 2f)
    val frames by produceState<List<Bitmap>>(emptyList(), dark, safeDepth) {
        value = withContext(Dispatchers.Default) { mistFrames(dark, safeDepth) }
    }
    // 30 updates/s avoid visible stepping while leaving UI animation independent.
    // Account for speed so 4x does not multiply the background's update rate.
    val ticks = (LocalMotionSettings.current.duration(72000) * 30 / 1000).coerceAtLeast(1)
    val sampledPhase = remember(phase, ticks) {
        derivedStateOf { ((phase?.value ?: 0f) * ticks).toInt().toFloat() / ticks }
    }
    Spacer(modifier.drawWithCache {
        val fallback = Brush.linearGradient(glassBackdropColors(dark, safeDepth), Offset.Zero, Offset(size.width, size.height))
        val destination = RectF(0f, 0f, size.width, size.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val mistShader = if (Build.VERSION.SDK_INT >= 33 && frames.isNotEmpty())
            GlassMistShader(frames, size.width, size.height) else null
        onDrawBehind {
            if (frames.isEmpty()) {
                drawRect(fallback)
            } else {
                drawIntoCanvas { canvas ->
                    if (Build.VERSION.SDK_INT >= 33 && mistShader != null) {
                        mistShader.draw(canvas.nativeCanvas, sampledPhase.value)
                    } else {
                        val position = (sampledPhase.value % 1f) * frames.size
                        val index = position.toInt()
                        val blend = position - index
                        paint.alpha = 255
                        canvas.nativeCanvas.drawBitmap(frames[index], null, destination, paint)
                        if (blend > 0f) {
                            paint.alpha = (blend * 255f).roundToInt()
                            canvas.nativeCanvas.drawBitmap(frames[(index + 1) % frames.size], null, destination, paint)
                        }
                    }
                }
            }
        }
    })
}

/** FP16 preserves sub-8-bit color steps; all 24 small textures together use 864 KiB. */
private suspend fun mistFrames(dark: Boolean, depth: Float): List<Bitmap> {
    val pale = if (dark) listOf(0xFF214457, 0xFF3B3459, 0xFF50364C, 0xFF244B48, 0xFF4B433A)
        else listOf(0xFFCCE6F5, 0xFFDBD0EE, 0xFFF5D8DF, 0xFFD8EDE6, 0xFFF5E7CE)
    val rich = if (dark) listOf(0xFF28677C, 0xFF574477, 0xFF744A64, 0xFF2D7068, 0xFF6E5C44)
        else listOf(0xFF9EC6E8, 0xFFB7A0D8, 0xFFE6B2CB, 0xFFB0DAD0, 0xFFE9D4AA)
    val colors = pale.indices.map { lerp(Color(pale[it]), Color(rich[it]), depth / 2f) }
    val base = if (dark) Color(0xFF152539) else Color(0xFFEDF1F8)
    val frames = ArrayList<Bitmap>(24)
    val pixels = ByteBuffer.allocateDirect(48 * 96 * 8).order(ByteOrder.nativeOrder()).asShortBuffer()
    val cx = FloatArray(5)
    val cy = FloatArray(5)
    for (frame in 0 until 24) {
        currentCoroutineContext().ensureActive()
        pixels.clear()
        val t = frame * (2 * PI / 24).toFloat()
        for (i in colors.indices) {
            val seed = i * (2 * PI / 5).toFloat()
            cx[i] = cos(seed + t) * 0.92f
            cy[i] = sin(seed - t) * 0.82f
        }
        for (y in 0 until 96) {
            val v = y / 95f * 2f - 1f
            val warpX = sin(v * 2.7f + t) * 0.22f
            for (x in 0 until 48) {
                val u = x / 47f * 2f - 1f
                val warpY = sin(u * 2.3f - t) * 0.19f
                var weight = 0.24f
                var red = base.red * weight
                var green = base.green * weight
                var blue = base.blue * weight
                for (i in colors.indices) {
                    val dx = u + warpX - cx[i]
                    val dy = v + warpY - cy[i]
                    // Broad overlapping Gaussian fields have no identifiable boundary.
                    val w = exp(-(dx * dx * 2.2f + dy * dy * 2.2f))
                    weight += w
                    red += colors[i].red * w
                    green += colors[i].green * w
                    blue += colors[i].blue * w
                }
                pixels.putHalfColor(red / weight, green / weight, blue / weight)
            }
        }
        pixels.rewind()
        frames += Bitmap.createBitmap(48, 96, Bitmap.Config.RGBA_F16, true,
            ColorSpace.get(ColorSpace.Named.SRGB)).apply { copyPixelsFromBuffer(pixels) }
    }
    return frames
}

// These are raw FP16 storage bits, not short-valued color arithmetic. Lint incorrectly
// treats ShortBuffer.put's fluent return value as numeric widening of its argument.
@SuppressLint("HalfFloat")
private fun ShortBuffer.putHalfColor(red: Float, green: Float, blue: Float) {
    put(Half.toHalf(red))
    put(Half.toHalf(green))
    put(Half.toHalf(blue))
    put(Half.toHalf(1f))
}
