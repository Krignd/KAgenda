package com.kstudio.agenda.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Stable
class DialogAnchor {
    var bounds by mutableStateOf<Rect?>(null)
        internal set
}

@Composable
fun rememberDialogAnchor(): DialogAnchor = remember { DialogAnchor() }

/** Screen coordinates remain consistent across the activity and the dialog's window. */
@Composable
fun Modifier.dialogAnchor(anchor: DialogAnchor): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coordinates ->
        val screen = IntArray(2)
        val window = IntArray(2)
        view.getLocationOnScreen(screen)
        view.getLocationInWindow(window)
        val topLeft = coordinates.positionInWindow() + Offset(
            (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat(),
        )
        anchor.bounds = Rect(topLeft, androidx.compose.ui.geometry.Size(
            coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
        ))
    }
}

/** A full-window host lets the surface travel all the way from its launcher, without clipping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OriginAlertDialog(
    origin: Rect,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: (@Composable () -> Unit)?,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    shape: Shape,
    containerColor: Color,
    iconContentColor: Color,
    titleContentColor: Color,
    textContentColor: Color,
    tonalElevation: Dp,
    properties: DialogProperties,
    scrollText: Boolean,
    entranceDurationMillis: Int,
    onEntered: (() -> Unit)?,
    contentReady: Boolean,
) {
    // A full-size native window avoids WRAP_CONTENT feeding the keyboard-reduced
    // dialog height back into the next measurement when the IME closes.
    val motion = LocalMotionSettings.current
    val progress = remember { Animatable(0f) }
    val entranceLayer = rememberGraphicsLayer()
    var captured by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    var capturedSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val enteredCallback by rememberUpdatedState(onEntered)
    var destination by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(captured) {
        if (captured) {
            // Prepare the frosted content at its final coordinates before moving its retained layer.
            // Haze must not resample a different page region for every scale/translation frame.
            withFrameNanos { }
            progress.animateTo(1f, tween(motion.duration(entranceDurationMillis), easing = FastOutSlowInEasing))
            entered = true
            enteredCallback?.invoke()
        }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val density = LocalDensity.current
        var visibleHeightPx by remember { mutableStateOf(dialogView.resources.displayMetrics.heightPixels) }
        DisposableEffect(dialogView) {
            // The full-size native window keeps this frame independent of content height.
            // Use it as the sole IME source: floating windows can consume Compose IME insets.
            val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val frame = android.graphics.Rect()
                dialogView.getWindowVisibleDisplayFrame(frame)
                if (frame.height() > 0) visibleHeightPx = frame.height()
            }
            dialogView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose { dialogView.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setWindowAnimations(0)
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(with(density) { visibleHeightPx.toDp() }).pointerInput(properties.dismissOnClickOutside, onDismissRequest) {
                detectTapGestures { if (properties.dismissOnClickOutside) onDismissRequest() }
            }.padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val view = LocalView.current
            androidx.compose.material3.Surface(
                modifier = modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val screen = IntArray(2)
                        val window = IntArray(2)
                        view.getLocationOnScreen(screen)
                        view.getLocationInWindow(window)
                        val topLeft = coordinates.positionInWindow() + Offset(
                            (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat(),
                        )
                        destination = Rect(topLeft, androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
                        ))
                    }.drawWithContent {
                        if (entered) {
                            drawContent()
                        } else {
                            if (!contentReady) return@drawWithContent
                            val pixels = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                            if (!captured || capturedSize != pixels) {
                                entranceLayer.record { this@drawWithContent.drawContent() }
                                capturedSize = pixels
                                captured = true
                            }
                        }
                    }.pointerInput(Unit) { detectTapGestures { /* consume the surface, not its children */ } }
                    .glassFrost(shape, popup = true),
                shape = shape, color = containerColor, tonalElevation = tonalElevation,
            ) {
                Column(Modifier.padding(24.dp)) {
                    icon?.let {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) { it() }
                        }
                    }
                    title?.let {
                        Box(Modifier.padding(bottom = 16.dp)) {
                            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() }
                            }
                        }
                    }
                    text?.let {
                        Box(Modifier.weight(1f, fill = false)
                            .then(if (scrollText) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(bottom = 24.dp)) {
                            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                            }
                        }
                    }
                    FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
            if (!entered) Canvas(Modifier.matchParentSize().onGloballyPositioned { coordinates ->
                val screen = IntArray(2)
                val window = IntArray(2)
                view.getLocationOnScreen(screen)
                view.getLocationInWindow(window)
                overlayOrigin = coordinates.positionInWindow() + Offset(
                    (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat(),
                )
            }.pointerInput(Unit) {
                // Do not let an invisible, stationary editor take input while its layer travels.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }) {
                val target = destination
                if (captured && target != null) {
                    val p = progress.value
                    val sx = (origin.width / target.width).coerceIn(0.03f, 1f)
                    val sy = (origin.height / target.height).coerceIn(0.03f, 1f)
                    entranceLayer.alpha = if (p == 0f) 0.01f else (p * 6f).coerceAtMost(1f)
                    translate(target.left - overlayOrigin.x + (origin.center.x - target.center.x) * (1f - p),
                        target.top - overlayOrigin.y + (origin.center.y - target.center.y) * (1f - p)) {
                        scale(sx + (1f - sx) * p, sy + (1f - sy) * p,
                            pivot = Offset(target.width / 2f, target.height / 2f)) {
                            drawLayer(entranceLayer)
                        }
                    }
                }
            }
        }
        }
    }
}
