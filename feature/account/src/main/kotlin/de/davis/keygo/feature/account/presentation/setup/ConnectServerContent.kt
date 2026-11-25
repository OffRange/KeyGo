package de.davis.keygo.feature.account.presentation.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.ui.components.Wizard
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.model.ChooseUsernameState
import de.davis.keygo.feature.account.presentation.setup.model.ConfirmPasswordState
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiState
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.SignInOrOutState
import de.davis.keygo.feature.account.presentation.setup.transformation.UsernameInputTransformation

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConnectServerContent(
    state: ConnectServerUiState,
    usernameInputTransformation: InputTransformation,
    onEvent: (ConnectServerUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    Wizard(
        steps = state.steps,
        currentStep = state.step,
        title = null,
        onBack = { onEvent(ConnectServerUiEvent.Back) },
        onContinue = { onEvent(ConnectServerUiEvent.Continue) },
        navigateUp = navigateUp,
        showContinueButton = state.step != ConnectServerStep.SignInOrOut,
        canContinue = state.canContinue,
        secondaryAction = state.step.secondaryAction?.let { action ->
            { SecondaryActionButton(action = action, onEvent = onEvent) }
        },
        continueButtonContent = {
            if (state.isChecking) LoadingIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                color = LocalContentColor.current,
            )
            else Text(text = stringResource(state.step.continueLabel))
        },
    ) { step ->
        when (step) {
            ConnectServerStep.EnterDomain -> EnterDomainContent(
                state = state.enterDomainState,
                onEvent = onEvent,
            )

            ConnectServerStep.SignInOrOut -> SignInOrOutContent(
                state = state.signInOrOutState,
                onEvent = onEvent,
            )

            ConnectServerStep.ChooseUsername -> ChooseUsernameContent(
                state = state.chooseUsernameState,
                usernameInputTransformation = usernameInputTransformation,
            )

            ConnectServerStep.ConfirmPassword -> ConfirmPasswordContent(
                state = state.confirmPasswordState,
            )
        }
    }
}

private val ConnectServerUiState.isChecking: Boolean
    get() = step == ConnectServerStep.ConfirmPassword &&
            confirmPasswordState.check == PasswordCheck.Checking

private val ConnectServerStep.continueLabel: Int
    get() = when (this) {
        ConnectServerStep.ConfirmPassword -> R.string.create_account
        else -> R.string.continue_step
    }

private data class SecondaryAction(
    @StringRes val label: Int,
    val event: ConnectServerUiEvent,
    val icon: ImageVector? = null,
)

private val ConnectServerStep.secondaryAction: SecondaryAction?
    get() = when (this) {
        ConnectServerStep.EnterDomain -> SecondaryAction(
            label = R.string.connect_server_scan_invite,
            event = ConnectServerUiEvent.ScanInvite,
            icon = Icons.Default.QrCodeScanner,
        )

        ConnectServerStep.SignInOrOut,
        ConnectServerStep.ChooseUsername,
        ConnectServerStep.ConfirmPassword -> null
    }

@Composable
private fun SecondaryActionButton(
    action: SecondaryAction,
    onEvent: (ConnectServerUiEvent) -> Unit,
) {
    TextButton(
        onClick = { onEvent(action.event) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        action.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .size(ButtonDefaults.IconSize),
            )
        }
        Text(text = stringResource(action.label))
    }
}

private class ConnectServerUiStateProvider : PreviewParameterProvider<ConnectServerUiState> {

    override val values = ConnectServerStep.entries.asSequence().map {
        ConnectServerUiState(
            enterDomainState = EnterDomainState(
                addressTextFieldState = TextFieldState("vault.example.org"),
                check = ServerCheck.Found(
                    serverUrl = "https://vault.example.org",
                    version = "0.4.0",
                    publicUrl = "https://vault.example.org",
                    inviteOnly = true,
                ),
            ),
            signInOrOutState = SignInOrOutState(
                serverHost = "vault.example.org",
                serverVersion = "0.4.0",
                signupsOpen = false,
            ),
            chooseUsernameState = ChooseUsernameState(
                usernameTextFieldState = TextFieldState("ada"),
                inviteTextFieldState = TextFieldState("K7QM2XRA9F3T"),
                serverHost = "vault.example.org",
                serverInviteRequired = true,
                inviteEditable = false,
                allowedUsernameChars = listOf("a-z", "0-9", ".", "_", "-"),
            ),
            confirmPasswordState = ConfirmPasswordState(
                passwordTextFieldState = TextFieldState(),
            ),
            step = it,
        )
    }
}

@Preview
@Composable
private fun ConnectServerContentPreview(
    @PreviewParameter(ConnectServerUiStateProvider::class) state: ConnectServerUiState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConnectServerContent(
                state = state,
                usernameInputTransformation = UsernameInputTransformation(String::lowercase),
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}
