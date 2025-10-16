package de.davis.keygo.core.identity.biometric.data.repository

import android.content.Context
import androidx.biometric.BiometricManager
import de.davis.keygo.core.identity.biometric.domain.model.BiometricAvailability
import de.davis.keygo.core.identity.biometric.domain.model.BiometricClass
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricAvailabilityRepository
import org.koin.core.annotation.Single

@Single
class BiometricAvailabilityRepositoryImpl(
    private val context: Context
) : BiometricAvailabilityRepository {

    private val biometricManager by lazy { BiometricManager.from(context) }

    override fun availability(biometricClass: BiometricClass): BiometricAvailability {
        val authenticators = when (biometricClass) {
            BiometricClass.Class2 -> BiometricManager.Authenticators.BIOMETRIC_WEAK
            BiometricClass.Class3 -> BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.Unavailable(BiometricAvailability.Reason.NoHardware)

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.Unavailable(BiometricAvailability.Reason.HardwareUnavailable)

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.Unavailable(BiometricAvailability.Reason.NoneEnrolled)

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.Unavailable(BiometricAvailability.Reason.SecurityUpdateRequired)

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricAvailability.Unavailable(BiometricAvailability.Reason.Unsupported)

            else -> BiometricAvailability.Unavailable(BiometricAvailability.Reason.Unknown)
        }
    }
}