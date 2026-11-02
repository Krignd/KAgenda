package com.kstudio.agenda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal fun preferenceFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float =
    ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

/** Align the reference label's centre with exactly the same inset and scale as the thumb. */
@Composable
fun PreferenceSliderScale(range: ClosedFloatingPointRange<Float>, startLabel: String, endLabel: String,
    referenceValue: Float, referenceLabel: String, referenceEnabled: Boolean, onReference: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
        Text(startLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopStart))
        Text(endLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopEnd))
        val referenceX = 14.dp + (maxWidth - 28.dp) * preferenceFraction(referenceValue, range)
        AppTextButton(onClick = onReference, enabled = referenceEnabled, contentPadding = PaddingValues(2.dp),
            modifier = Modifier.offset(x = referenceX - 50.dp, y = 14.dp).width(100.dp)) {
            Text(referenceLabel, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A quiet 6dp track with a round lens, inside a full 48dp touch target. */
@Composable
fun PreferenceSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String,
    onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val lens = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 1f)
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value, range)
        setProgress { target -> change(target.coerceIn(range)); finish(); true }
    }.pointerInput(range, rtl) {
        fun update(x: Float) {
            val inset = 14.dp.toPx()
            val fraction = ((x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
            change(range.start + (if (rtl) 1f - fraction else fraction) * (range.endInclusive - range.start))
        }
        detectTapGestures { update(it.x); finish() }
    }.pointerInput(range, rtl) {
        fun update(x: Float) {
            val inset = 14.dp.toPx()
            val f = ((x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
            change(range.start + (if (rtl) 1f - f else f) * (range.endInclusive - range.start))
        }
        detectDragGestures(onDragStart = { update(it.x) }, onDragEnd = { finish() },
            onDragCancel = { finish() }) { event, _ -> event.consume(); update(event.position.x) }
    }) {
        val inset = 14.dp.toPx()
        val width = size.width - inset * 2
        val fraction = preferenceFraction(value, range)
        val x = inset + width * (if (rtl) 1f - fraction else fraction)
        val cy = size.height / 2
        val h = 6.dp.toPx()
        drawRoundRect(track, Offset(inset, cy - h / 2), Size(width, h), CornerRadius(h))
        val start = if (rtl) x else inset
        drawRoundRect(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.45f), primary)),
            Offset(start, cy - h / 2), Size(width * fraction, h), CornerRadius(h))
        drawCircle(primary.copy(alpha = 0.12f), 14.dp.toPx(), Offset(x, cy))
        drawCircle(primary, 10.dp.toPx(), Offset(x, cy))
        drawCircle(lens, 7.dp.toPx(), Offset(x, cy))
        drawCircle(primary, 2.5.dp.toPx(), Offset(x, cy))
    }
}
