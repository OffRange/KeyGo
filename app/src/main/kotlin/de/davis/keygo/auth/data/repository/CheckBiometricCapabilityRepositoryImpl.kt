package de.davis.keygo.auth.data.repository

import android.content.Context
import androidx.biometric.BiometricManager
import de.davis.keygo.auth.domain.model.BiometricCapability
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.repository.CheckBiometricCapabilityRepository

class CheckBiometricCapabilityRepositoryImpl(
    private val context: Context
) : CheckBiometricCapabilityRepository {

    private val biometricManager by lazy { BiometricManager.from(context) }

    override fun getCapability(request: BiometricRequest): BiometricCapability {
        val authenticators = when (request) {
            is BiometricRequest.Class2 -> BiometricManager.Authenticators.BIOMETRIC_WEAK
            BiometricRequest.Class3 -> BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.Available

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricCapability.Unavailable(BiometricCapability.Reason.NoHardware)

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricCapability.Unavailable(BiometricCapability.Reason.HardwareUnavailable)

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricCapability.Unavailable(BiometricCapability.Reason.NoneEnrolled)

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricCapability.Unavailable(BiometricCapability.Reason.SecurityUpdateRequired)

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricCapability.Unavailable(BiometricCapability.Reason.Unsupported)

            else -> BiometricCapability.Unavailable(BiometricCapability.Reason.Unknown)
        }
    }
}