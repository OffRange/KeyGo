package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.components.SetupPasswordField
import de.davis.keygo.feature.account.presentation.setup.model.SignInCheck
import de.davis.keygo.feature.account.presentation.setup.model.SignInState
import de.davis.keygo.feature.account.presentation.setup.transformation.UsernameInputTransformation

@Composable
internal fun SignInContent(
    state: SignInState,
    usernameInputTransformation: InputTransformation,
) {
    val checking = state.check == SignInCheck.Checking
    val error = (state.check as? SignInCheck.Failed)?.error

    AccountSetupScaffold(
        title = stringResource(R.string.sign_in),
        description = stringResource(R.string.sign_in_screen_description, state.serverHost),
        icon = Icons.AutoMirrored.Default.Login,
    ) {
        OutlinedTextField(
            state = state.usernameTextFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username },
            label = { Text(text = stringResource(R.string.username)) },
            enabled = !checking,
            // A wrong username and a wrong password read the same; the error sits on the password.
            isError = error == SyncError.InvalidCredentials,
            inputTransformation = usernameInputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupPasswordField(
            state = state.passwordTextFieldState,
            modifier = Modifier.semantics { contentType = ContentType.Password },
            label = { Text(text = stringResource(R.string.sign_in_password_label)) },
            enabled = !checking,
            isError = error != null,
            supportingText = error?.let {
                { Text(text = stringResource(it.messageRes)) }
            },
            imeAction = ImeAction.Done,
        )
    }
}

private class SignInCheckProvider : PreviewParameterProvider<SignInCheck> {

    override val values = sequenceOf(
        SignInCheck.Idle,
        SignInCheck.Checking,
        SignInCheck.Failed(SyncError.InvalidCredentials),
        SignInCheck.Failed(SyncError.AccountMismatch),
        SignInCheck.Failed(SyncError.Network(NetworkFailure.Timeout)),
    )
}

@Preview
@Composable
private fun SignInContentPreview(
    @PreviewParameter(SignInCheckProvider::class) check: SignInCheck,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SignInContent(
                state = SignInState(
                    usernameTextFieldState = TextFieldState("ada"),
                    passwordTextFieldState = TextFieldState("hunter2"),
                    serverHost = "vault.example.org",
                    check = check,
                ),
                usernameInputTransformation = UsernameInputTransformation(String::lowercase),
            )
        }
    }
}
