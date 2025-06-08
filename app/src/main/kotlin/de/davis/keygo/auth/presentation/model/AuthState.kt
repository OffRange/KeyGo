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


    sealed interface BiometricAuthState : AuthState {
        val biometricsAvailable: Boolean
        val useBiometrics: Boolean

        fun copyBiometricState(
            biometricsAvailable: Boolean = this.biometricsAvailable,
            useBiometrics: Boolean = this.useBiometrics,
        ): BiometricAuthState = when (this) {
            is CreateAccess -> copy(
                biometricsAvailable = biometricsAvailable,
                useBiometrics = useBiometrics
            )

            is Migrating -> copy(
                biometricsAvailable = biometricsAvailable,
                useBiometrics = useBiometrics
            )
        }
    }

    data class Migrating(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UIPasswordError = UIPasswordError.None,
        override val loading: Boolean = false,
        override val biometricsAvailable: Boolean = false,
        override val useBiometrics: Boolean = true,
        val showMigrationDialog: Boolean = true
    ) : AuthState, BiometricAuthState

    data class CreateAccess(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UIPasswordError = UIPasswordError.None,
        override val loading: Boolean = false,
        override val biometricsAvailable: Boolean = false,
        override val useBiometrics: Boolean = true,
        val confirmPasswordTextFieldState: TextFieldState = TextFieldState(),
        val confirmPasswordError: UIPasswordError = UIPasswordError.None,
        val score: Score = Score.None,
    ) : AuthState, BiometricAuthState

    fun copyDefaultState(
        loading: Boolean = this.loading,
        passwordTextFieldState: TextFieldState = this.passwordTextFieldState,
        passwordError: UIPasswordError = this.passwordError,
    ): AuthState = when (this) {
        is Login -> copy(
            loading = loading,
            passwordTextFieldState = passwordTextFieldState,
            passwordError = passwordError,
        )

        is CreateAccess -> copy(
            loading = loading,
            passwordTextFieldState = passwordTextFieldState,
            passwordError = passwordError,
        )

        is Migrating -> copy(
            loading = loading,
            passwordTextFieldState = passwordTextFieldState,
            passwordError = passwordError,
        )
    }
}
