package de.davis.keygo.auth.domain.repository

import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData

interface BiometricWrappedKeyRepository {

    suspend fun getBiometricWrappedKeyData(): BiometricWrappedKeyData?
    suspend fun setBiometricWrappedKeyData(wrappedKey: ByteArray? = null, iv: ByteArray? = null)
}