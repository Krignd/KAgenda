package com.kstudio.agenda.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    private val accents = listOf(
        Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6),
        Color(0xFFEF4444), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
        Color.Black, Color.White, Color(0xFF101520), Color(0xFFFFEDBA),
    )

    @Test
    fun accentsRemainReadableOnLightAndDarkTintedCards() {
        for ((background, foreground) in listOf(
            Color(0xFFF8FAFD) to Color(0xFF0F172A),
            Color(0xFF1C293F) to Color(0xFFE6ECF6),
        )) {
            for (accent in accents) {
                for (tintAlpha in listOf(0f, 0.15f, 0.22f)) {
                    val surface = accent.copy(alpha = tintAlpha).compositeOver(background)
                    val adjusted = readableAccent(accent, surface, foreground)
                    val a = adjusted.luminance()
                    val b = surface.luminance()
                    val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                    assertTrue("Contrast $ratio for $accent on $surface", ratio >= 4.5f)
                    assertEquals(1f, adjusted.alpha, 0.001f)
                }
            }
        }
    }

    @Test
    fun readableAccentsKeepTheirOriginalColor() {
        val accent = Color(0xFF1D4ED8)
        assertEquals(accent, readableAccent(accent, Color.White, Color.Black))
    }
}
