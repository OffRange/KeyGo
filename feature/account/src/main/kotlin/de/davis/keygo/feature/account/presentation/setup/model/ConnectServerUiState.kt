package de.davis.keygo.feature.account.presentation.setup.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.sync.domain.model.SyncError

@Stable
internal data class ConnectServerUiState(
    val enterDomainState: EnterDomainState,
    val signInOrOutState: SignInOrOutState,
    val chooseUsernameState: ChooseUsernameState,
    val confirmPasswordState: ConfirmPasswordState,
    val signInState: SignInState,
    val step: ConnectServerStep = ConnectServerStep.EnterDomain,
    val steps: List<ConnectServerStep> = stepsFor(choice = null),
    val canContinue: Boolean = false,
)

@Stable
internal data class EnterDomainState(
    val addressTextFieldState: TextFieldState,
    val typedScheme: String? = null,
    val check: ServerCheck = ServerCheck.Idle,
)

@Stable
internal data class SignInOrOutState(
    val serverHost: String,
    val serverVersion: String?,
    val signupsOpen: Boolean,
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
    val inviteError: SyncError? = null,
)

@Stable
internal data class ConfirmPasswordState(
    val passwordTextFieldState: TextFieldState,
    val check: PasswordCheck = PasswordCheck.Idle,
)

@Stable
internal data class SignInState(
    val usernameTextFieldState: TextFieldState,
    val passwordTextFieldState: TextFieldState,
    val serverHost: String,
    val check: SignInCheck = SignInCheck.Idle,
)
