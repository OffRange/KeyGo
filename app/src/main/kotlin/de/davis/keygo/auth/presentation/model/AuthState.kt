package de.davis.keygo.auth.presentation.model

data class AuthState(
    val authEvent: AuthEvent = AuthEvent.None,
    val password: String = "",
    val passwordError: UIPasswordError = UIPasswordError.None,
    val biometricsAvailable: Boolean = false,
)
