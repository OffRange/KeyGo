package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.domain.model.CiphertextData

internal data class ChangePasswordState(
    val currentPassword: TextFieldState = TextFieldState(),
    val newPassword: TextFieldState = TextFieldState(),
    val confirmPassword: TextFieldState = TextFieldState(),
    val passwordScore: PasswordScore = PasswordScore.None,
    val currentPasswordError: FieldError = FieldError.None,
    val newPasswordError: FieldError = FieldError.None,
    val confirmPasswordError: FieldError = FieldError.None,
    /** Non-null when biometric verification is offered; carries the wrapped biometric ARK. */
    val biometricCiphertext: CiphertextData? = null,
    /** True while the master-password fallback dialog is shown (biometric users). */
    val showReauthDialog: Boolean = false,
    val loading: Boolean = false,
) {
    val canUseBiometric: Boolean get() = biometricCiphertext != null
}

internal sealed interface FieldError {
    data object None : FieldError
    data object Empty : FieldError
    data object Incorrect : FieldError
    data object Mismatch : FieldError
}

internal sealed interface ChangePasswordEvent {
    data object Success : ChangePasswordEvent
    data object GenericError : ChangePasswordEvent

    /** Ask the screen to launch the biometric prompt (it owns the controller). */
    data object LaunchBiometricPrompt : ChangePasswordEvent
}
