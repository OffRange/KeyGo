package de.davis.keygo.auth.domain.repository

import de.davis.keygo.auth.domain.model.KeyStoreError
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.model.crypto.AesKey

interface BiometricKekRepository {

    fun hasKek(): Boolean
    fun getKek(): Result<AesKey, KeyStoreError>
    fun createKek(): AesKey
}
