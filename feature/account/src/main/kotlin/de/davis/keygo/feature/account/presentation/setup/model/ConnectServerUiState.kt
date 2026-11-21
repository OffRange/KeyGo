package de.davis.keygo.feature.account.presentation.setup.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable

@Stable
internal data class ConnectServerUiState(
    val enterDomainState: EnterDomainState,
    val chooseUsernameState: ChooseUsernameState,
    val confirmPasswordState: ConfirmPasswordState,
    val step: ConnectServerStep = ConnectServerStep.EnterDomain,
    val canContinue: Boolean = false,
) {
    val steps: List<ConnectServerStep> = ConnectServerStep.entries
}

@Stable
internal data class EnterDomainState(
    val addressTextFieldState: TextFieldState,
    val typedScheme: String? = null,
    val check: ServerCheck = ServerCheck.Idle,
)

@Stable
internal data class ChooseUsernameState(
    val usernameTextFieldState: TextFieldState,
    val inviteTextFieldState: TextFieldState,
    val serverHost: String,
    val serverInviteRequired: Boolean,
    val inviteEditable: Boolean,
    val allowedUsernameChars: List<String>,
    val usernameCheck: UsernameCheck = UsernameCheck.Idle,
)

@Stable
internal data class ConfirmPasswordState(
    val passwordTextFieldState: TextFieldState,
    val check: PasswordCheck = PasswordCheck.Idle,
)
