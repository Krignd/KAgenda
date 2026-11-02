package com.kstudio.agenda.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/** Speed is a multiplier; duration and spring stiffness must scale consistently. */
data class MotionSettings(val speedPercent: Int = 100) {
    val speed: Float get() = speedPercent.coerceIn(25, 400) / 100f
    fun duration(milliseconds: Int): Int = (milliseconds / speed).roundToInt().coerceAtLeast(1)
    fun stiffness(value: Float): Float = value * speed * speed
}

val LocalMotionSettings = staticCompositionLocalOf { MotionSettings() }
