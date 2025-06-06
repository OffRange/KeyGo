package de.davis.keygo.auth.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.domain.model.Score

sealed interface AuthState {

    val passwordTextFieldState: TextFieldState
    val passwordError: UIPasswordError
    val loading: Boolean

    data class Login(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UIPasswordError = UIPasswordError.None,
        override val loading: Boolean = false,
        val biometricAuthenticationAvailable: Boolean = false,
    ) : AuthState

    data class CreateAccess(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UIPasswordError = UIPasswordError.None,
        override val loading: Boolean = false,
        val biometricsAvailable: Boolean = false,
        val useBiometrics: Boolean = true,
        val confirmPasswordTextFieldState: TextFieldState = TextFieldState(),
        val confirmPasswordError: UIPasswordError = UIPasswordError.None,
        val score: Score = Score.None,
    ) : AuthState

    fun copyDefaultState(
        loading: Boolean = this.loading,
        passwordTextFieldState: TextFieldState = this.passwordTextFieldState,
        passwordError: UIPasswordError = this.passwordError
    ): AuthState = when (this) {
        is Login -> copy(
            loading = loading,
            passwordTextFieldState = passwordTextFieldState,
            passwordError = passwordError
        )

        is CreateAccess -> copy(
            loading = loading,
            passwordTextFieldState = passwordTextFieldState,
            passwordError = passwordError,
        )
    }
}
