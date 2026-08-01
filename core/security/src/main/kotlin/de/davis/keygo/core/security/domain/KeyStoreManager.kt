package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import javax.crypto.Cipher

interface KeyStoreManager {

    fun getOrCreateCipherFor(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray? = null
    ): Cipher

    fun deleteKey(keyId: KeyId)
}