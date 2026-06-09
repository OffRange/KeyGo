package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.biometric.BiometricPrompt

/** What to do when the biometric prompt does not yield a key. */
internal enum class BiometricFallback {
    /** User explicitly chose the password path, or biometric is unavailable (locked out or permanently disabled). */
    UsePassword,

    /** Transient dismissal (back press, timeout); leave the form as-is. */
    Cancelled,
}

/** Maps a [BiometricPrompt] error code to the fallback the change-password screen should take. */
internal fun biometricFallbackFor(errorCode: Int): BiometricFallback = when (errorCode) {
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> BiometricFallback.UsePassword

    else -> BiometricFallback.Cancelled
}
