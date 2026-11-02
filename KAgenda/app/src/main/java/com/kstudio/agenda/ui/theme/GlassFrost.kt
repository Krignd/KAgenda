package com.kstudio.agenda.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

// Cards sample only the scenery. Dialogs sample the page in the other window.
// Keeping these sources separate prevents a card from sampling itself.
internal val LocalGlassScenery = staticCompositionLocalOf<HazeState?> { null }
internal val LocalGlassPage = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.glassFrost(shape: Shape = RectangleShape, popup: Boolean = false): Modifier {
    if (!LocalGlassEnabled.current) return this
    val source = (if (popup) LocalGlassPage.current else LocalGlassScenery.current) ?: return this
    val base = if (isSystemInDarkTheme()) Color(0xFF142035) else Color(0xFFEDF3FC)
    return clip(shape).hazeChild(
        source,
        HazeStyle(
            backgroundColor = base,
            tint = HazeTint(Color.Transparent),
            blurRadius = if (popup) 32.dp else 24.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(base),
        ),
    )
}

/** Background-only blur, followed by the surface tint, followed by sharp content. */
@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.glassFrost(shape), shape = shape, color = color,
        contentColor = contentColor, tonalElevation = tonalElevation,
        shadowElevation = shadowElevation, border = border, content = content,
    )
}

/** Retains Material dialog behavior, focus, insets and dismissal semantics. */
@Composable
fun FrostedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    origin: androidx.compose.ui.geometry.Rect? = null,
    scrollText: Boolean = true,
    entranceDurationMillis: Int = 480,
    onEntered: (() -> Unit)? = null,
    contentReady: Boolean = true,
) {
    if (origin != null) {
        OriginAlertDialog(origin, onDismissRequest, confirmButton, modifier, dismissButton, icon,
            title, text, shape, containerColor, iconContentColor, titleContentColor,
            textContentColor, tonalElevation, properties, scrollText, entranceDurationMillis, onEntered, contentReady)
        return
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { onEntered?.invoke() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        modifier = modifier.glassFrost(shape, popup = true),
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = shape, containerColor = containerColor,
        iconContentColor = iconContentColor, titleContentColor = titleContentColor,
        textContentColor = textContentColor, tonalElevation = tonalElevation, properties = properties,
    )
}
