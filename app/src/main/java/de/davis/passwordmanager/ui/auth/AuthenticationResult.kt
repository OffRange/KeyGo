package de.davis.passwordmanager.ui.auth

sealed class AuthenticationResult {

    data object Success : AuthenticationResult()
    data class Error(val authenticationState: AuthenticationState) : AuthenticationResult()
}
