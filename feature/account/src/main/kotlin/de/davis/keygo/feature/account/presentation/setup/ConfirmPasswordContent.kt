package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.model.ConfirmPasswordState

@Composable
internal fun ConfirmPasswordContent(
    state: ConfirmPasswordState,
) {
    AccountSetupScaffold(
        title = stringResource(R.string.confirm_account_password_title),
        description = stringResource(R.string.confirm_account_password_description),
        icon = Icons.Default.Password,
    ) {
        var passwordHidden by rememberSaveable { mutableStateOf(true) }
        OutlinedSecureTextField(
            state = state.passwordTextFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.confirm_account_password_label)) },
            textObfuscationMode = if (passwordHidden) TextObfuscationMode.RevealLastTyped
            else TextObfuscationMode.Visible,
            trailingIcon = {
                VisibilityButton(
                    isHidden = passwordHidden,
                    onClick = { passwordHidden = !passwordHidden },
                )
            },
        )
    }
}

@Preview
@Composable
private fun ConfirmPasswordContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConfirmPasswordContent(
                state = ConfirmPasswordState(
                    passwordTextFieldState = TextFieldState()
                )
            )
        }
    }
}
