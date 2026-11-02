package com.kstudio.agenda.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Keep window power saving from reducing UI frame rate on Android 15+. */
@Composable
internal fun PreferHighRefreshRate() {
    if (Build.VERSION.SDK_INT < 35) return
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    DisposableEffect(window) {
        val original = window.isFrameRatePowerSavingsBalanced
        // Do not declare preferredRefreshRate/displayModeId: some HyperOS versions
        // classify these requests as app-managed refresh and impose a 60 Hz cap.
        window.setFrameRatePowerSavingsBalanced(false)
        onDispose { window.setFrameRatePowerSavingsBalanced(original) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}
