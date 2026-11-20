package de.davis.keygo.feature.account.presentation.setup.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable

@Stable
internal data class ConnectServerUiState(
    val enterDomainState: EnterDomainState,
    val chooseUsernameState: ChooseUsernameState,
    val step: ConnectServerStep = ConnectServerStep.EnterDomain,
) {
    val steps: List<ConnectServerStep> = ConnectServerStep.entries

    val canContinue: Boolean
        get() = when (step) {
            ConnectServerStep.EnterDomain -> enterDomainState.check is ServerCheck.Found
            ConnectServerStep.ChooseUsername -> chooseUsernameState.canContinue
        }
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
) {
    val canContinue: Boolean
        get() = usernameCheck is UsernameCheck.Valid &&
                (!serverInviteRequired || inviteTextFieldState.text.isNotBlank())
}
