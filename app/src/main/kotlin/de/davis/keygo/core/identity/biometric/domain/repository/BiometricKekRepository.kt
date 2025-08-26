package de.davis.keygo.core.identity.biometric.domain.repository

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.identity.biometric.domain.model.KeyStoreError

interface BiometricKekRepository {

    fun hasKek(): Boolean
    fun getKek(): Result<AesKey, KeyStoreError>
    fun createKek(): AesKey
}