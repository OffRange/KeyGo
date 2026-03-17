package de.davis.keygo.core.security.data.repository

import android.content.Context
import androidx.biometric.BiometricManager
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import org.koin.core.annotation.Single

@Single
internal class BiometricAvailabilityRepositoryImpl(
    private val context: Context
) : BiometricAvailabilityRepository {

    private val biometricManager by lazy { BiometricManager.from(context) }

    override fun availability(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
}