package com.kstudio.agenda.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

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
 * @param threshold 触发阈值（建议 36~48dp；默认 40dp，滑动更省力）
 * @param canStep 该方向是否允许切换（返回 false 则不抢占，交给内层横向滚动）
 * @param onStep 切换回调：+1 = 向后（下一日/周/月），-1 = 向前
 */
fun Modifier.swipeStep(
    key: Any? = Unit,
    enabled: Boolean = true,
    threshold: Dp = 40.dp,
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
                    // 水平略占优即认为用户想翻页（1.05 倍，允许斜向滑动）；否则视为纵向滚动，不再干扰
                    capture = abs(dx) > abs(dy) * 1.05f && canStep(if (dx < 0f) 1 else -1)
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
 * 双指缩放（pinch）：仅在两指按下时生效并消费手势；单指拖动完全放行，
 * 不影响内层滚动与 swipeStep 翻页。
 */
fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoomChange = event.calculateZoom()
                if (zoomChange.isFinite() && zoomChange != 1f) onZoom(zoomChange)
                // 双指期间消化事件：防止内层滚动/翻页抖动
                event.changes.forEach { if (it.pressed) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * 定位闪烁会话：一次「定位并闪烁」（通知 / 小组件点击进入）共用一个起点时刻。
 * 闪烁相位按“会话起点 + 绝对时间”计算，因此：
 * - 进入应用后第 0 帧即最亮（不用等动画起步）；
 * - 切换日/周/月视图、列表项重新进入组合都不会重新计时，闪烁总时长固定、不会被反复切换“续期”。
 */
object FlashSession {
    private val _startedAt = MutableStateFlow(0L)

    /** 会话起点（毫秒时间戳；0 = 无进行中的闪烁会话） */
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    fun start(millis: Long = System.currentTimeMillis()) {
        _startedAt.value = millis
    }

    fun clear() {
        _startedAt.value = 0L
    }
}

/** 一次闪烁的起伏次数与单次周期（3 次 × 400ms ≈ 1.2 秒闪完） */
private const val FLASH_PULSES = 3
private const val FLASH_PERIOD_MILLIS = 400

private val FLASH_TOTAL_MILLIS = (FLASH_PULSES * FLASH_PERIOD_MILLIS).toLong()

/**
 * 闪烁波形：t=0 时为峰值（立刻可见），t=总时长时为 0（自然结束，不会停留高亮）。
 */
private fun flashWave(elapsedMillis: Long): Float {
    if (elapsedMillis <= 0L) return 1f
    if (elapsedMillis >= FLASH_TOTAL_MILLIS) return 0f
    val total = FLASH_TOTAL_MILLIS.toFloat()
    val angle = (2.0 * PI * (FLASH_PULSES - 0.5) * elapsedMillis / total).toFloat()
    return 0.5f + 0.5f * cos(angle)
}

/**
 * 定位闪烁脉冲值：0..1（0=基线、1=峰值）。
 * [active] 为 true 时按 [FlashSession] 的会话起点计算相位（总时长固定）；
 * [active] 为 false 时固定返回 0f，不创建动画。
 */
@Composable
fun rememberFlashPulse(active: Boolean): Float {
    if (!active) return 0f
    val sessionStart by FlashSession.startedAt.collectAsState()
    // 会话起点缺失时（例如外部直接传 flash=true）退化为「从现在开始」
    val start = sessionStart.takeIf { it > 0L } ?: System.currentTimeMillis()
    var pulse by remember(start) {
        mutableFloatStateOf(flashWave(System.currentTimeMillis() - start))
    }
    LaunchedEffect(start) {
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            pulse = flashWave(elapsed)
            if (elapsed >= FLASH_TOTAL_MILLIS) break
            withFrameNanos { }
        }
    }
    return pulse
}
