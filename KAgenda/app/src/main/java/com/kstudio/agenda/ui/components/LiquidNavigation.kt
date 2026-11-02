package com.kstudio.agenda.ui.components

import com.kstudio.agenda.ui.theme.LocalMotionSettings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.ui.theme.FrostedSurface
import com.kstudio.agenda.ui.theme.LocalGlassEnabled
import kotlin.math.abs
import kotlin.math.roundToInt

/** One continuous pool of light. Separate springs let its front pull its tail. */
@Composable
fun LiquidNavigation(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, icons: List<@Composable () -> Unit>? = null,
) {
    if (labels.isEmpty()) return
    val motion = LocalMotionSettings.current
    val dark = isSystemInDarkTheme()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestSelect by rememberUpdatedState(onSelect)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val front by animateFloatAsState(dragged ?: selectedIndex.toFloat(), spring(0.72f, motion.stiffness(310f)), label = "liquidFront")
    val tail by animateFloatAsState(dragged ?: selectedIndex.toFloat(), spring(0.86f, motion.stiffness(135f)), label = "liquidTail")
    val shape = RoundedCornerShape(if (icons == null) 28.dp else 30.dp)
    FrostedSurface(
        modifier = modifier.cardGlassBorder(shape), shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = if (icons == null) 56.dp else 80.dp)
                .selectableGroup().padding(4.dp)
                .pointerInput(labels.size, rtl) {
                    fun position(x: Float): Float {
                        val fraction = (x / size.width).coerceIn(0f, 1f)
                        return ((if (rtl) 1f - fraction else fraction) * labels.size - 0.5f)
                            .coerceIn(0f, labels.lastIndex.toFloat())
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = position(it.x) },
                        onHorizontalDrag = { change, _ -> change.consume(); dragged = position(change.position.x) },
                        onDragEnd = { dragged?.let { latestSelect(it.roundToInt()) }; dragged = null },
                        onDragCancel = { dragged = null },
                    )
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val cell = size.width / labels.size
                val a = front
                val b = tail
                val centreIndex = a * 0.72f + b * 0.28f
                val centre = ((if (rtl) labels.lastIndex - centreIndex else centreIndex) + 0.5f) * cell
                val stretch = (abs(a - b) * 0.58f).coerceAtMost(0.82f)
                val halfWidth = cell * (0.47f + stretch)
                val inset = if (icons == null) 0f else 2.dp.toPx()
                val halfHeight = (size.height / 2f - inset) * (1f - stretch * 0.18f)
                val cy = size.height / 2f
                val left = (centre - halfWidth).coerceAtLeast(0f)
                val right = (centre + halfWidth).coerceAtMost(size.width)
                val w = right - left
                // The asymmetry changes sign when reversing direction; no fixed-duration keyframes.
                val lean = ((a - b) * (if (rtl) -1 else 1)).coerceIn(-1f, 1f) * halfHeight * 0.18f
                val path = Path().apply {
                    moveTo(left + w * 0.5f, cy - halfHeight)
                    cubicTo(right - w * 0.10f, cy - halfHeight - lean, right, cy - halfHeight * 0.70f, right, cy)
                    cubicTo(right, cy + halfHeight * 0.75f, right - w * 0.12f, cy + halfHeight + lean, left + w * 0.5f, cy + halfHeight)
                    cubicTo(left + w * 0.10f, cy + halfHeight - lean, left, cy + halfHeight * 0.70f, left, cy)
                    cubicTo(left, cy - halfHeight * 0.75f, left + w * 0.12f, cy - halfHeight + lean, left + w * 0.5f, cy - halfHeight)
                    close()
                }
                drawPath(path, Brush.verticalGradient(if (dark) listOf(Color(0x70466E9E), Color(0x503C5A80))
                    else listOf(Color(0xC0C1E1FF), Color(0x9ED3E9FC))))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    val selected = selectedIndex == index
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(motion.duration(180)), label = "liquidLabel",
                    )
                    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(0.8f, motion.stiffness(320f)), label = "liquidIcon")
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .selectable(selected, role = Role.Tab, onClick = { latestSelect(index) })
                            .heightIn(min = if (icons == null) 48.dp else 72.dp)
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        icons?.getOrNull(index)?.let { icon ->
                            CompositionLocalProvider(LocalContentColor provides color) {
                                Box(Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale }) { icon() }
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        Text(label, color = color,
                            style = if (icons == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Move one incoming layer; do not render two full sets of frosted calendar cells together. */
@Composable
fun AnimatedPage(index: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val glass = LocalGlassEnabled.current
    val motion = LocalMotionSettings.current
    var previous by remember { mutableIntStateOf(index) }
    val direction = remember(index) { if (index >= previous) 1 else -1 }
    val arrival = remember(index) { Animatable(if (index == previous) 1f else 0f) }
    LaunchedEffect(index) {
        previous = index
        // Give the incoming page one frame for layout and blur setup before moving its layer.
        withFrameNanos { }
        arrival.animateTo(1f, tween(motion.duration(if (glass) 300 else 220)))
    }
    Box(modifier.graphicsLayer {
        alpha = 0.15f + arrival.value * 0.85f
        translationX = direction * (1f - arrival.value) * 40.dp.toPx()
    }) { content(index) }
}

@Composable
fun Modifier.pageEntrance(key: Any): Modifier {
    val motion = LocalMotionSettings.current
    val arrival = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { arrival.animateTo(1f, tween(motion.duration(300))) }
    return graphicsLayer {
        alpha = 0.25f + arrival.value * 0.75f
        translationY = (1f - arrival.value) * 10.dp.toPx()
    }
}
