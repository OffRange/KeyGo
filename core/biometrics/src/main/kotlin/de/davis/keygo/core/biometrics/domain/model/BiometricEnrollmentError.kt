package de.davis.keygo.core.biometrics.domain.model

sealed interface BiometricEnrollmentError {
    data object NoActiveAccount : BiometricEnrollmentError
    data object NoActiveSession : BiometricEnrollmentError
    data object WrappingFailed : BiometricEnrollmentError
    data object PersistenceFailed : BiometricEnrollmentError
    data class BiometricFailed(val error: BiometricAuthError) : BiometricEnrollmentError
}

fun BiometricEnrollmentError.isUserDismissal(): Boolean =
    this is BiometricEnrollmentError.BiometricFailed &&
            (error == BiometricAuthError.Declined || error == BiometricAuthError.Canceled)
