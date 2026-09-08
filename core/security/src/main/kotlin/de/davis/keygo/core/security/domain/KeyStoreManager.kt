package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import javax.crypto.Cipher

interface KeyStoreManager {

    fun getOrCreateCipherFor(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray? = null
    ): Result<Cipher, Throwable>

    fun deleteKey(keyId: KeyId)
}