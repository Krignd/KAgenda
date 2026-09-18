package com.kstudio.agenda.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 页面级横向滑动切换（日/周/月视图通用）。
 *
 * 设计要点（兼顾“少误触”与“容易识别”）：
 * - 在 Initial 阶段观察手势：水平位移超过触摸阈值、且明显大于垂直位移（1.2 倍）才判定为翻页手势；
 * - 判定成功后若 [canStep] 允许该方向（如内层网格已滚到边界），则消耗事件并对内层滚动“让位”，
 *   滑动累计超过 [threshold]（默认 60dp）时触发一步；
 * - 一次完整手势最多触发一步；点击（位移极小）不会触发。
 *
 * @param key 重建手势监听的 key
 * @param enabled 是否启用
 * @param threshold 触发阈值（建议 56~72dp）
 * @param canStep 该方向是否允许切换（返回 false 则不抢占，交给内层横向滚动）
 * @param onStep 切换回调：+1 = 向后（下一日/周/月），-1 = 向前
 */
fun Modifier.swipeStep(
    key: Any? = Unit,
    enabled: Boolean = true,
    threshold: Dp = 60.dp,
    canStep: (Int) -> Boolean = { true },
    onStep: (Int) -> Unit,
): Modifier = composed {
    pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        val thresholdPx = threshold.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            var decided = false
            var capture = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!decided && (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop)) {
                    decided = true
                    // 水平占优（1.2 倍）才认为用户想翻页；否则视为纵向滚动，不再干扰
                    capture = abs(dx) > abs(dy) * 1.2f && canStep(if (dx < 0f) 1 else -1)
                }
                if (!decided) continue
                if (!capture) break
                change.consume()
                if (abs(dx) >= thresholdPx) {
                    onStep(if (dx < 0f) 1 else -1)
                    break
                }
            }
        }
    }
}

/**
 * 闪烁反馈透明度（0.25 ↔ 1 循环）。
 * [active] 为 false 时返回固定 1f，不会创建动画（避免无谓的重组）。
 */
@Composable
fun rememberFlashAlpha(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "flash")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "flashAlpha",
    )
    return alpha
}
