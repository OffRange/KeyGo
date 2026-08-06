package de.davis.keygo.feature.onboarding.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError

internal sealed interface OnboardingUiState {

    data class Welcome(val migrating: Boolean = false) : OnboardingUiState

    data class SetMainPassword(
        val passwordTextFieldState: TextFieldState,
        val confirmPasswordTextFieldState: TextFieldState,
        val passwordScore: PasswordScore,
        val passwordError: UiFieldError? = null,
        val confirmPasswordError: UiFieldError? = null,
    ) : OnboardingUiState

    data object EnableBiometrics : OnboardingUiState
    data object ImportData : OnboardingUiState
    data object EnableAutofill : OnboardingUiState
}
