package de.davis.keygo.feature.auth.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError

sealed interface AuthState {

    data object Loading : AuthState

    sealed interface Interactable : AuthState {
        val passwordTextFieldState: TextFieldState
        val passwordError: UiFieldError?
        val loading: Boolean

        fun copyDefaultState(
            loading: Boolean = this.loading,
            passwordTextFieldState: TextFieldState = this.passwordTextFieldState,
            passwordError: UiFieldError? = this.passwordError,
        ): Interactable = when (this) {
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

    data class Login(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UiFieldError? = null,
        override val loading: Boolean = false,
        val biometricAuthenticationAvailable: Boolean = false,
    ) : Interactable


    sealed interface BiometricAuthState : Interactable {
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
        override val passwordError: UiFieldError? = null,
        override val loading: Boolean = false,
        override val biometricsAvailable: Boolean = false,
        override val useBiometrics: Boolean = true,
        val showMigrationDialog: Boolean = true
    ) : BiometricAuthState

    data class CreateAccess(
        override val passwordTextFieldState: TextFieldState,
        override val passwordError: UiFieldError? = null,
        override val loading: Boolean = false,
        override val biometricsAvailable: Boolean = false,
        override val useBiometrics: Boolean = true,
        val confirmPasswordTextFieldState: TextFieldState = TextFieldState(),
        val confirmPasswordError: UiFieldError? = null,
        val passwordScore: PasswordScore = PasswordScore.None,
    ) : BiometricAuthState
}
