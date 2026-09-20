package com.kstudio.agenda.ui

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI 助手界面互斥：同一时刻只保留「最后打开」的那一个界面，避免同时出现两个输入界面。
 * - [Kind.InApp]：应用内对话框（MainScreen 的 AI 助手对话框）
 * - [Kind.Overlay]：系统悬浮球的输入面板（FloatingBallService）
 *
 * 规则：先打开 A 再打开 B → 只保留 B（A 自动收起）；
 * 两者几乎同时打开（[SIMULTANEOUS_MS] 时间窗内）→ 优先保留悬浮球面板。
 */
object AiSurface {

    enum class Kind { None, InApp, Overlay }

    /** 视为「同时打开」的时间窗（毫秒） */
    private const val SIMULTANEOUS_MS = 600L

    private val _state = MutableStateFlow(Kind.None)
    val state: StateFlow<Kind> = _state.asStateFlow()

    private var overlayOpenedAt = 0L

    /** 悬浮球面板打开：始终成为当前界面（对方界面由各自的观察者收起） */
    @Synchronized
    fun openOverlay() {
        overlayOpenedAt = SystemClock.elapsedRealtime()
        _state.value = Kind.Overlay
    }

    /**
     * 应用内对话框请求打开。
     * @return false = 与悬浮球面板几乎同时打开，按「悬浮球优先」放弃本次打开
     */
    @Synchronized
    fun openInApp(): Boolean {
        if (_state.value == Kind.Overlay &&
            SystemClock.elapsedRealtime() - overlayOpenedAt < SIMULTANEOUS_MS
        ) {
            return false
        }
        _state.value = Kind.InApp
        return true
    }

    /** 关闭指定界面：仅当当前界面确实是自己时才清空（避免误关对方刚打开的界面） */
    @Synchronized
    fun close(kind: Kind) {
        if (_state.value == kind) _state.value = Kind.None
    }
}
