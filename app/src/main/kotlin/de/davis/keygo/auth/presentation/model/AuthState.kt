package de.davis.keygo.auth.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.domain.model.Score

data class AuthState(
    val authEvent: AuthEvent = AuthEvent.None,
    val authMode: Mode = Mode.Register(),
    val biometricsAvailable: Boolean = false,
    val loading: Boolean = false,
) {
    sealed interface Mode {
        val score: Score
        val passwordTextFieldState: TextFieldState
        val passwordError: UIPasswordError

        data class Login(
            override val score: Score = Score.None,
            override val passwordTextFieldState: TextFieldState = TextFieldState(),
            override val passwordError: UIPasswordError = UIPasswordError.None,
        ) : Mode

        data class Register(
            override val score: Score = Score.None,
            override val passwordTextFieldState: TextFieldState = TextFieldState(),
            override val passwordError: UIPasswordError = UIPasswordError.None,
            val confirmPasswordTextFieldState: TextFieldState = TextFieldState(),
            val confirmPasswordError: UIPasswordError = UIPasswordError.None,
            val useBiometrics: Boolean = true,
        ) : Mode

        @Suppress("UNCHECKED_CAST")
        fun <M : Mode> copyDefaults(
            passwordTextFieldState: TextFieldState = this.passwordTextFieldState,
            passwordError: UIPasswordError = this.passwordError,
            score: Score = this.score
        ): M = when (this) {
            is Login -> copy(
                passwordTextFieldState = passwordTextFieldState,
                passwordError = passwordError,
                score = score
            )

            is Register -> copy(
                passwordTextFieldState = passwordTextFieldState,
                passwordError = passwordError,
                score = score
            )
        } as M
    }
}
