package client.wexmanager.feature.editor.golang

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Shared native Expressive controls; colors and motion follow WexTheme. */
@Composable
internal fun GoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    tooltip: String? = null,
    content: @Composable () -> Unit,
) {
    @Composable fun control() {
        if (tonal) FilledTonalIconButton(onClick = onClick, modifier = modifier, enabled = enabled,
            shapes = IconButtonDefaults.shapes(), content = content)
        else IconButton(onClick = onClick, modifier = modifier, enabled = enabled,
            shapes = IconButtonDefaults.shapes(), content = content)
    }
    if (tooltip == null) control() else GoTooltip(tooltip) { control() }
}

@Composable
internal fun GoTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState()) { content() }
}

@Composable
internal fun GoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    shape: Shape = OutlinedTextFieldDefaults.roundedShape,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        singleLine = singleLine, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, supportingText = supportingText, isError = isError,
        shape = shape, colors = OutlinedTextFieldDefaults.tonalColors(),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
}
