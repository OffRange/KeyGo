package de.davis.keygo.core.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.ui.R

sealed interface UiFieldError {

    data object Incorrect : UiFieldError
    data object Mismatch : UiFieldError
    data object Empty : UiFieldError
}

val UiFieldError.error: String
    @Composable
    get() = stringResource(
        when (this) {
            is UiFieldError.Incorrect -> R.string.incorrect_password
            is UiFieldError.Mismatch -> R.string.password_does_not_match
            is UiFieldError.Empty -> R.string.blank_password
        }
    )