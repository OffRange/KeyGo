package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.util.Result

@Deprecated("Use UseCases instead")
interface BiometricEnrollmentAdapter {


    @Deprecated("Use DisableBiometricsUseCase instead")
    suspend fun disableBiometric(): Result<Unit, BiometricEnrollmentError>
}
