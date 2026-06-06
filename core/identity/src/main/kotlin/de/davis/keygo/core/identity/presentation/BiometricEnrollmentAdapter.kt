package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result

interface BiometricEnrollmentAdapter {

    suspend fun BiometricCryptoController.requestEnableBiometric(
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Unit, BiometricEnrollmentError>

    suspend fun disableBiometric(): Result<Unit, BiometricEnrollmentError>
}

inline fun BiometricEnrollmentAdapter.useEnrollmentAdapter(
    block: BiometricEnrollmentAdapter.() -> Result<Unit, BiometricEnrollmentError>,
): Result<Unit, BiometricEnrollmentError> = with(this) { block() }