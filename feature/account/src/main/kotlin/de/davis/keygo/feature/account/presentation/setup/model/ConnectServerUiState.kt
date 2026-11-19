package de.davis.keygo.feature.account.presentation.setup.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable

@Stable
internal data class ConnectServerUiState(
    val enterDomainState: EnterDomainState,
    val step: ConnectServerStep = ConnectServerStep.EnterDomain,
) {
    val steps: List<ConnectServerStep> = ConnectServerStep.entries

    val canContinue: Boolean = when (step) {
        ConnectServerStep.EnterDomain -> enterDomainState.check is ServerCheck.Found
    }
}

@Stable
internal data class EnterDomainState(
    val addressTextFieldState: TextFieldState,
    val typedScheme: String? = null,
    val check: ServerCheck = ServerCheck.Idle,
)
