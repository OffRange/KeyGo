package de.davis.keygo.feature.auth.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.ui.model.UiFieldError

sealed interface AuthState {

    data object Loading : AuthState

    data class Login(
        val passwordTextFieldState: TextFieldState,
        val passwordError: UiFieldError? = null,
        val loading: Boolean = false,
        val biometricAuthenticationAvailable: Boolean = false,
    ) : AuthState
}
