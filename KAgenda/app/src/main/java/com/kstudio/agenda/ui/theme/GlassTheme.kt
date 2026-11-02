package com.kstudio.agenda.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Liquid glass uses white / slate tints over cached, already defocused color scenery.
 * Cards reveal the mist directly; popup windows use Haze to blur page text and cards.
 * Android 12L+ uses GPU popup blur; older versions retain a dense frosted fallback.
 * Form outlines are neutral and visible. Decorative dark card borders remain absent.
 */
val LocalGlassEnabled = staticCompositionLocalOf { false }
internal val LocalGlassColorDepth = staticCompositionLocalOf { 0f }
internal fun glassDepthFraction(depth: Int): Float = depth.coerceIn(0, 200) / 100f

/** 玻璃风格 · 浅色：表面带 alpha，叠加在渐变背景上 */
private val GlassLight = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xCCDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xCCCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFFB45309),
    background = Color.Transparent,
    onBackground = Color(0xFF0F172A),
    surface = Color(0x99FFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFDCE5F1),
    onSurfaceVariant = Color(0xFF41506B),
    surfaceContainerLowest = Color(0x59FFFFFF),
    // Popup tint: dense enough to mask glyphs; a blurred page supplies the remaining color.
    surfaceContainerLow = Color(0xADFFFFFF),
    surfaceContainer = Color(0xA3FFFFFF),
    // Dialogs use the same blurred, dense material.
    surfaceContainerHigh = Color(0xADFFFFFF),
    surfaceContainerHighest = Color(0xFFE6EDF7),
    // 通透风格不需要色调叠加（否则半透明表面会被染上一层主色雾感）
    surfaceTint = Color.Transparent,
    // Form outlines; decorative card highlights are defined separately in Common.kt.
    outline = Color(0xFF7D8DA5),
    outlineVariant = Color(0xFFB3C0D2),
    error = Color(0xFFDC2626),
)

/** 玻璃风格 · 深色 */
private val GlassDark = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF0B2559),
    primaryContainer = Color(0x99374C86),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF5EEAD4),
    secondaryContainer = Color(0x99115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFF0B96B),
    background = Color.Transparent,
    onBackground = Color(0xFFE6ECF6),
    surface = Color(0xAD212E43),
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Color(0xFF394A65),
    onSurfaceVariant = Color(0xFFB9C6DA),
    surfaceContainerLowest = Color(0x66132033),
    // Dark popup surfaces preserve the same optical density as light surfaces.
    surfaceContainerLow = Color(0xB8212E43),
    surfaceContainer = Color(0xB0212E43),
    surfaceContainerHigh = Color(0xB8253349),
    surfaceContainerHighest = Color(0xFF3B4D69),
    surfaceTint = Color.Transparent,
    // Visible neutral outlines for controls; dark card borders stay transparent.
    outline = Color(0xFF8D9EB8),
    outlineVariant = Color(0xFF61738F),
    error = Color(0xFFF87171),
)

/** Interpolates the previous soft material into the richer, more translucent material. */
internal fun glassScheme(dark: Boolean, depth: Float) = (if (dark) GlassDark else GlassLight).let { rich ->
    val amount = depth.coerceIn(0f, 1f)
    val extra = (depth - 1f).coerceIn(0f, 1f)
    rich.copy(
        surface = lerp(if (dark) Color(0xC7212E43) else Color(0xC7FFFFFF), lerp(rich.surface, if (dark) Color(0xA0212E43) else Color(0x80FFFFFF), extra), amount),
        surfaceContainerLow = lerp(if (dark) Color(0xD1212E43) else Color(0xD1FFFFFF), lerp(rich.surfaceContainerLow, if (dark) Color(0xAD212E43) else Color(0x99FFFFFF), extra), amount),
        surfaceContainer = lerp(if (dark) Color(0xCC212E43) else Color(0xCCFFFFFF), lerp(rich.surfaceContainer, if (dark) Color(0xA6212E43) else Color(0x90FFFFFF), extra), amount),
        surfaceContainerHigh = lerp(if (dark) Color(0xD1253349) else Color(0xD1FFFFFF), lerp(rich.surfaceContainerHigh, if (dark) Color(0xAD253349) else Color(0x99FFFFFF), extra), amount),
    )
}

/** Shared scenery stops also used by widget drawable verification. Old depths retain their colors. */
internal fun glassBackdropColors(dark: Boolean, depth: Float): List<Color> {
    val amount = depth.coerceIn(0f, 1f)
    val extra = (depth - 1f).coerceIn(0f, 1f)
    return if (dark) listOf(
        lerp(Color(0xFF0B1424), Color(0xFF102A4A), extra),
        lerp(Color(0xFF17263D), Color(0xFF203D59), extra),
        lerp(Color(0xFF211E35), Color(0xFF3C294E), extra),
    ) else listOf(
        lerp(Color(0xFFE8F0FC), lerp(Color(0xFFCEDFFD), Color(0xFFA7C8F4), extra), amount),
        lerp(Color(0xFFF1EFF9), lerp(Color(0xFFE4DDF4), Color(0xFFC7B9ED), extra), amount),
        lerp(Color(0xFFE7F4F2), lerp(Color(0xFFCEF0EB), Color(0xFFA6E1DB), extra), amount),
    )
}

/** Scenery motion is opt-in and exists only while the activity is visible. */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animateBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val depth = LocalGlassColorDepth.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var visible by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // InfiniteTransition obeys the system animation duration scale. Reading its value only
    // inside the scenery draw pass avoids recomposing the timetable every frame.
    val phase = if (enabled && animateBackground && visible) {
        rememberInfiniteTransition(label = "glassScenery").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LocalMotionSettings.current.duration(72000), easing = LinearEasing), RepeatMode.Restart),
            label = "glassSceneryPhase",
        )
    } else null

    val page = remember { HazeState() }
    CompositionLocalProvider(
        // Scenery is already defocused; per-card blur adds no visible detail.
        // Popups still blur text/cards through the independent page source.
        LocalGlassScenery provides null,
        LocalGlassPage provides if (enabled) page else null,
    ) {
        Box(modifier.fillMaxSize().then(if (enabled) Modifier.haze(page) else Modifier.background(MaterialTheme.colorScheme.background))) {
            if (enabled) GlassScenery(Modifier.fillMaxSize(), dark, depth, phase)
            content()
        }
    }
}
