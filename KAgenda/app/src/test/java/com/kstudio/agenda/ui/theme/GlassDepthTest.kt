package com.kstudio.agenda.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class GlassDepthTest {
    @Test fun softDepthRestoresPreviousMaterialAndRichEndRetainsMoreColor() {
        assertEquals(Color(0xC7FFFFFF), glassScheme(false, 0f).surface)
        assertEquals(Color(0xD1FFFFFF), glassScheme(false, 0f).surfaceContainerHigh)
        assertEquals(Color(0x99FFFFFF), glassScheme(false, 1f).surface)
        assertEquals(0f, glassDepthFraction(-10), 0f)
        assertEquals(2f, glassDepthFraction(200), 0f)
        assertEquals(Color(0x80FFFFFF), glassScheme(false, 2f).surface)
        assertEquals(1f, glassDepthFraction(100), 0f)
        assertEquals(2f, glassDepthFraction(999), 0f)
        for (dark in listOf(false, true)) {
            val alphas = (0..20).map { glassScheme(dark, it / 10f).surface.alpha }
            assertTrue(alphas.zipWithNext().all { (a, b) -> a >= b })
        }
    }

    @Test fun primaryTextKeepsContrastAcrossDepthAndAppearanceModes() {
        for (dark in listOf(false, true)) {
            val scenery = if (dark) Color(0xFF17263D) else Color(0xFF9EBFF2)
            for (step in 0..20) {
                val scheme = glassScheme(dark, step / 10f)
                for (tint in listOf(scheme.surface, scheme.surfaceContainerHigh)) {
                    val background = tint.compositeOver(scenery).luminance()
                    val foreground = scheme.onSurface.luminance()
                    val contrast = (maxOf(background, foreground) + 0.05f) / (minOf(background, foreground) + 0.05f)
                    assertTrue("depth=$step dark=$dark contrast=$contrast", contrast >= 4.5f)
                }
            }
        }
    }
}
