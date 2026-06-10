package de.davis.keygo.core.identity.domain.model

import de.davis.keygo.core.security.domain.model.BiometricAuthError

sealed interface BiometricEnrollmentError {
    data object NoActiveAccount : BiometricEnrollmentError
    data object WrappingFailed : BiometricEnrollmentError
    data object PersistenceFailed : BiometricEnrollmentError
    data class BiometricFailed(val error: BiometricAuthError) : BiometricEnrollmentError
}