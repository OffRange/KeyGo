package de.davis.keygo.core.identity.domain.repository

import de.davis.keygo.core.identity.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.util.Result

/**
 * A repository that provides access to wrapped keys. These keys are wrapped using either biometric
 * authentication or the main password.The unwrapped key is the actual key used for encryption and
 * decryption operations.
 */
interface WrappedKeyRepository {

    suspend fun getBiometricWrappedKey(): Result<BiometricWrappedKeyData, Unit>
    suspend fun getPasswordWrappedKey(): Result<PasswordWrappedKeyData, Unit>

    suspend fun setBiometricWrappedKey(data: BiometricWrappedKeyData)
    suspend fun setPasswordWrappedKey(data: PasswordWrappedKeyData)
}