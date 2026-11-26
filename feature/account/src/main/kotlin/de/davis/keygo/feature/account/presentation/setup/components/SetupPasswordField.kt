package de.davis.keygo.feature.account.presentation.setup.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.davis.keygo.core.ui.components.VisibilityButton

@Composable
internal fun SetupPasswordField(
    state: TextFieldState,
    label: @Composable TextFieldLabelScope.() -> Unit,
    enabled: Boolean,
    isError: Boolean,
    supportingText: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Unspecified,
) {
    var passwordHidden by rememberSaveable { mutableStateOf(true) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = label,
        textObfuscationMode = if (passwordHidden) TextObfuscationMode.RevealLastTyped
        else TextObfuscationMode.Visible,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        trailingIcon = {
            VisibilityButton(
                isHidden = passwordHidden,
                onClick = { passwordHidden = !passwordHidden },
            )
        },
        // Built here rather than taken from callers: an explicit KeyboardOptions replaces
        // Material's secure defaults, which would let the keyboard learn the password.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
    )
}
