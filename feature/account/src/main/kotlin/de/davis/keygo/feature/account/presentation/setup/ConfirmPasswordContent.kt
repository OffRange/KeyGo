package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.ui.model.error
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.components.SetupPasswordField
import de.davis.keygo.feature.account.presentation.setup.model.ConfirmPasswordState
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck

@Composable
internal fun ConfirmPasswordContent(
    state: ConfirmPasswordState,
) {
    AccountSetupScaffold(
        title = stringResource(R.string.confirm_account_password_title),
        description = stringResource(R.string.confirm_account_password_description),
        icon = Icons.Default.Password,
    ) {
        SetupPasswordField(
            state = state.passwordTextFieldState,
            label = { Text(stringResource(R.string.confirm_account_password_label)) },
            enabled = state.check != PasswordCheck.Checking,
            isError = state.check != PasswordCheck.Idle && state.check != PasswordCheck.Checking,
            supportingText = when (val check = state.check) {
                PasswordCheck.Incorrect -> {
                    { Text(text = UiFieldError.Incorrect.error) }
                }

                PasswordCheck.Failed -> {
                    { Text(text = stringResource(R.string.confirm_account_password_failed)) }
                }

                is PasswordCheck.RegisterFailed -> {
                    { Text(text = stringResource(check.error.messageRes)) }
                }

                PasswordCheck.Idle,
                PasswordCheck.Checking,
                    -> null
            },
        )
    }
}

@Preview
@Composable
private fun ConfirmPasswordContentPreview(
    @PreviewParameter(PasswordCheckProvider::class) check: PasswordCheck,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConfirmPasswordContent(
                state = ConfirmPasswordState(
                    passwordTextFieldState = TextFieldState("hunter2"),
                    check = check,
                ),
            )
        }
    }
}

private class PasswordCheckProvider : PreviewParameterProvider<PasswordCheck> {

    override val values = sequenceOf(
        PasswordCheck.Idle,
        PasswordCheck.Checking,
        PasswordCheck.Incorrect,
        PasswordCheck.Failed,
        PasswordCheck.RegisterFailed(SyncError.Network(NetworkFailure.Timeout)),
    )
}
