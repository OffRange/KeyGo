package de.davis.passwordmanager.ui.auth

import androidx.annotation.StringRes

data class AuthenticationState(
    @StringRes val passwordError: Int? = null,
    @StringRes val newPasswordError: Int? = null,
    @StringRes val confirmPasswordError: Int? = null
)
