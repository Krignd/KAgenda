package com.kstudio.agenda.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 液态玻璃（Liquid Glass）界面风格。
 *
 * 设计要点（参考 macOS 天气 App 的材质与层级语言，不包含任何气象元素）：
 * - 两层内容：底层是柔和的低饱和渐变 + 两团柔光色块（制造空间深度）；
 * - 上层卡片/导航栏/顶栏使用半透明表面（surfaceContainer* 系列带 alpha），叠加在渐变上形成毛玻璃观感；
 * - 圆角保持较大弧度，文字层级沿用原配色，仅提升表面透明度与描边亮度。
 *
 * 说明：Compose 没有「背景模糊（backdrop blur）」能力（`Modifier.blur` 模糊的是自身内容，
 * 且需 API 31+），因此这里用「渐变底 + 半透明表面」实现等价观感，兼容 minSdk 26，
 * 且不需要任何额外依赖。
 */

/** 当前是否为液态玻璃风格（组件内可按需做额外适配） */
val LocalGlassEnabled = staticCompositionLocalOf { false }

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
    surface = Color(0xA6FFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0x66FFFFFF),
    onSurfaceVariant = Color(0xFF41506B),
    surfaceContainerLowest = Color(0x59FFFFFF),
    surfaceContainerLow = Color(0x8CFFFFFF),
    surfaceContainer = Color(0xA6FFFFFF),
    surfaceContainerHigh = Color(0xBAFFFFFF),
    surfaceContainerHighest = Color(0xCCFFFFFF),
    // 通透风格不需要色调叠加（否则半透明表面会被染上一层主色雾感）
    surfaceTint = Color.Transparent,
    // 描边用中性灰（此前用过主色蓝微调，会在卡片/输入框上出现“异常的蓝色描边”）
    outline = Color(0x26000000),
    outlineVariant = Color(0x14000000),
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
    surface = Color(0x8C1D2942),
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Color(0x4D33415C),
    onSurfaceVariant = Color(0xFFB9C6DA),
    surfaceContainerLowest = Color(0x66132033),
    surfaceContainerLow = Color(0x80172438),
    surfaceContainer = Color(0x8C1D2942),
    surfaceContainerHigh = Color(0x991F2D49),
    surfaceContainerHighest = Color(0xA6243355),
    surfaceTint = Color.Transparent,
    // 深色玻璃：不用纯白描边（黑色卡片上一圈白线很生硬），改用低透明度中性白作“玻璃边缘”
    outline = Color(0x2EFFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFF87171),
)

/** 是否为浅色玻璃色板 */
@Composable
internal fun glassScheme(dark: Boolean) = if (dark) GlassDark else GlassLight

/**
 * 液态玻璃背景：柔和渐变 + 两团柔光色块。
 * 内容（Scaffold 等）叠在其上，因其表面带 alpha 而形成毛玻璃观感。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) {
        Brush.linearGradient(
            listOf(Color(0xFF0A1120), Color(0xFF15233B), Color(0xFF1B1533)),
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFE7EEFB), Color(0xFFF2ECFB), Color(0xFFE4F5F1)),
        )
    }
    val glow1 = if (dark) Color(0x663B82F6) else Color(0x664C8DF6)
    val glow2 = if (dark) Color(0x5522D3EE) else Color(0x598B5CF6)
    // 第三团柔光（右下偏暖）：让半透明表面在多个区域都能看得到“透光”，避免大片像素级平色
    val glow3 = if (dark) Color(0x3DF472B6) else Color(0x40FBBF24)

    Box(modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val r1 = size.minDimension * 0.62f
            val c1 = Offset(size.width * 0.10f, size.height * 0.06f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1, Color.Transparent),
                    center = c1,
                    radius = r1,
                ),
                radius = r1,
                center = c1,
            )
            val r2 = size.minDimension * 0.55f
            val c2 = Offset(size.width * 0.98f, size.height * 0.72f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow2, Color.Transparent),
                    center = c2,
                    radius = r2,
                ),
                radius = r2,
                center = c2,
            )
            val r3 = size.minDimension * 0.44f
            val c3 = Offset(size.width * 0.16f, size.height * 0.99f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow3, Color.Transparent),
                    center = c3,
                    radius = r3,
                ),
                radius = r3,
                center = c3,
            )
        }
        CompositionLocalProvider(LocalGlassEnabled provides true) { content() }
    }
}
