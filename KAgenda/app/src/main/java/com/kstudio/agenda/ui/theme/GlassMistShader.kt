package com.kstudio.agenda.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** One pass: interpolate in float, then dither at screen-pixel scale before 8-bit output. */
@RequiresApi(33)
internal class GlassMistShader(frames: List<Bitmap>, private val width: Float, private val height: Float) {
    private val inputs = frames.map { bitmap ->
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    }
    private val shader = RuntimeShader("""
        uniform shader first;
        uniform shader second;
        uniform float2 resolution;
        uniform float blend;

        half4 main(float2 position) {
            float2 uv = position / resolution * float2(48.0, 96.0);
            float3 color = mix(float3(first.eval(uv).rgb), float3(second.eval(uv).rgb), blend);
            // Fixed spatial noise: no time input, so it never crawls or flickers.
            // Work in float to keep the hash stable on GPUs with native half precision.
            float3 p = fract(floor(position).xyx * 0.1031);
            p += dot(p, p.yzx + 33.33);
            float noise = fract((p.x + p.y) * p.z) - 0.5;
            return half4(clamp(color + noise / 255.0, 0.0, 1.0), 1.0);
        }
    """.trimIndent()).apply { setFloatUniform("resolution", width, height) }
    private val paint = Paint().apply { shader = this@GlassMistShader.shader; isDither = true }
    private var activeIndex = -1

    fun draw(canvas: Canvas, progress: Float) {
        val position = (progress % 1f) * inputs.size
        val index = position.toInt()
        if (index != activeIndex) {
            shader.setInputShader("first", inputs[index])
            shader.setInputShader("second", inputs[(index + 1) % inputs.size])
            activeIndex = index
        }
        shader.setFloatUniform("blend", position - index)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
