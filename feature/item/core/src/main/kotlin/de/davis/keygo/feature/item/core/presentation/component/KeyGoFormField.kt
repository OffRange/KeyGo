package de.davis.keygo.feature.item.core.presentation.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.item.core.R
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.transformation.TrimTransformation

@Composable
fun KeyGoFormField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    outsideTrailingContent: @Composable (() -> Unit)? = null,
    error: InputFieldError? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = TrimTransformation,
    outputTransformation: OutputTransformation? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val supportingText: @Composable (() -> Unit)? = error?.let {
        {
            val text = when (error) {
                InputFieldError.Empty -> stringResource(R.string.field_blank)
            }
            Text(text = text, color = MaterialTheme.colorScheme.error)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (isSecure) {
            true -> {
                var passwordHidden by rememberSaveable { mutableStateOf(true) }
                OutlinedSecureTextField(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .trimOnFocusLost(state, inputTransformation is TrimTransformation),
                    label = label,
                    placeholder = placeholder,
                    prefix = prefix,
                    textObfuscationMode = if (passwordHidden) {
                        TextObfuscationMode.RevealLastTyped
                    } else {
                        TextObfuscationMode.Visible
                    },
                    trailingIcon = {
                        VisibilityButton(
                            isHidden = passwordHidden,
                            onClick = { passwordHidden = !passwordHidden },
                        )
                    },
                    supportingText = supportingText,
                    isError = supportingText != null,
                    keyboardOptions = keyboardOptions,
                    onKeyboardAction = onKeyboardAction,
                    inputTransformation = inputTransformation,
                    interactionSource = interactionSource
                )
            }

            else -> OutlinedTextField(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .trimOnFocusLost(state, inputTransformation is TrimTransformation),
                label = label,
                placeholder = placeholder,
                prefix = prefix,
                trailingIcon = trailingContent,
                lineLimits = lineLimits,
                supportingText = supportingText,
                isError = supportingText != null,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                interactionSource = interactionSource
            )
        }

        outsideTrailingContent?.let {
            Box(modifier = Modifier.padding(top = minimizedLabelHalfHeight())) {
                it()
            }
        }
    }
}

private fun Modifier.trimOnFocusLost(
    state: TextFieldState,
    enable: Boolean
): Modifier = if (enable) {
    onFocusChanged {
        if (!it.hasFocus) {
            state.setTextAndPlaceCursorAtEnd(
                state.text.trim().toString()
            )
        }
    }
} else this


/**
 * Copied from android's internal source code
 * Source: OutlinedTextField padding
 */
@Composable
internal fun minimizedLabelHalfHeight(): Dp {
    val compositionLocalValue = MaterialTheme.typography.bodySmall.lineHeight
    val fallbackValue = 16.sp
    val value = if (compositionLocalValue.isSp) compositionLocalValue else fallbackValue
    return with(LocalDensity.current) { value.toDp() / 2 }
}