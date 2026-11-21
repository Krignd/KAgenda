package com.kstudio.agenda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Cached, static lighting: apply inside a clipped Surface or after a background. */
@Composable
fun Modifier.glassSheen(): Modifier {
    if (!LocalGlassEnabled.current) return this
    val dark = isSystemInDarkTheme()
    val reflection = 0.16f - LocalGlassColorDepth.current * 0.06f
    return drawWithCache {
        val light = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (dark) 0.055f else reflection),
                Color.White.copy(alpha = if (dark) 0.012f else 0.06f),
                Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(size.width * 0.65f, size.height),
        )
        onDrawBehind { drawRect(light) }
    }
}

/** Chrome is denser than cards so controls remain legible over the backdrop. */
@Composable
fun glassChromeColor(): Color = if (isSystemInDarkTheme()) {
    lerp(Color(0xD41B293E), Color(0xC21B293E), LocalGlassColorDepth.current.coerceIn(0f, 1f))
} else {
    lerp(Color(0xD4F7FAFF), Color(0xB8F7FAFF), LocalGlassColorDepth.current.coerceIn(0f, 1f))
}

/** Full-screen dialogs cover the underlying window with a solid theme color. */
@Composable
fun overlayBackgroundColor(): Color = if (LocalGlassEnabled.current) {
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)
} else MaterialTheme.colorScheme.background.copy(alpha = 1f)

/** Preserve accent hue while lifting dark colors / deepening pale colors for text. */
@Composable
fun glassAccentColor(accent: Color, container: Color? = null): Color {
    if (!LocalGlassEnabled.current) return accent
    // Account for the darker blue glow now visible through the less dense white coat.
    val background = if (isSystemInDarkTheme()) Color(0xFF17263D) else androidx.compose.ui.graphics.lerp(Color(0xFF9EBFF2), Color(0xFF79A8EE), (LocalGlassColorDepth.current - 1f).coerceIn(0f, 1f))
    val surface = (container ?: MaterialTheme.colorScheme.surface).compositeOver(background)
    // Check against the brightest part of the static reflection as well.
    val litSurface = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.055f else 0.16f - LocalGlassColorDepth.current * 0.06f).compositeOver(surface)
    val foreground = MaterialTheme.colorScheme.onSurface
    val adjusted = readableAccent(accent, surface, foreground)
    return readableAccent(adjusted, litSurface, foreground)
}

internal fun readableAccent(accent: Color, background: Color, foreground: Color): Color {
    val opaque = accent.copy(alpha = 1f)
    fun contrast(color: Color): Float {
        val a = color.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    if (contrast(opaque) >= 4.5f) return opaque
    // Find the smallest adjustment rather than washing every accent to white.
    var low = 0f
    var high = 1f
    repeat(10) {
        val mid = (low + high) / 2f
        if (contrast(lerp(opaque, foreground, mid)) >= 4.5f) high = mid else low = mid
    }
    return lerp(opaque, foreground, high)
}
