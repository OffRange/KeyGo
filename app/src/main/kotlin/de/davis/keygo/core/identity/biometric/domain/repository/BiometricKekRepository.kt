package de.davis.keygo.core.identity.biometric.domain.repository

import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.identity.biometric.domain.model.KeyStoreError
import de.davis.keygo.core.util.Result

@Deprecated("Migrate to :core:security")
interface BiometricKekRepository {

    fun hasKek(): Boolean
    fun getKek(): Result<AesKey, KeyStoreError>
    fun createKek(): AesKey
}