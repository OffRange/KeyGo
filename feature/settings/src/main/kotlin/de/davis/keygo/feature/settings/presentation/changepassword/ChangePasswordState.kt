package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError

@Stable
internal data class ChangePasswordState(
    val currentPassword: TextFieldState = TextFieldState(),
    val newPassword: TextFieldState = TextFieldState(),
    val confirmPassword: TextFieldState = TextFieldState(),
    val passwordScore: PasswordScore = PasswordScore.None,
    val currentPasswordError: UiFieldError? = null,
    val newPasswordError: UiFieldError? = null,
    val confirmPasswordError: UiFieldError? = null,
    val biometricAvailable: Boolean = false,
    /** True while the master-password fallback dialog is shown (biometric users). */
    val showReauthDialog: Boolean = false,
    val loading: Boolean = false,
)

internal sealed interface ChangePasswordEvent {
    data object Success : ChangePasswordEvent
    data object GenericError : ChangePasswordEvent
}
