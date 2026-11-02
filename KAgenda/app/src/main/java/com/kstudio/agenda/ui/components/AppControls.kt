package com.kstudio.agenda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.ui.theme.LocalGlassEnabled

/** Shared contrast rules; Material retains focus, ripple, keyboard and accessibility behavior. */
@Composable
fun AppButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            .compositeOver(MaterialTheme.colorScheme.surface.copy(alpha = 1f)),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    ),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) = Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.65f)),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) = OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)

@Composable
fun AppTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    ),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) = TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
    colors = colors, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content)

@Composable
fun AppSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
    ),
    interactionSource: MutableInteractionSource? = null,
) = Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)

@Composable
fun AppOutlinedTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = if (LocalGlassEnabled.current) RoundedCornerShape(14.dp) else OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedContainerColor = if (LocalGlassEnabled.current) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f) else Color.Transparent,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    ),
) = OutlinedTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder,
    leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation,
    keyboardOptions, keyboardActions, singleLine, maxLines, minLines, interactionSource, shape, colors)
