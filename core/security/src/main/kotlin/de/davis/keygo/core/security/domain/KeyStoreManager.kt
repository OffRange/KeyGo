package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.model.CipherKeyId
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.Result
import javax.crypto.Cipher

interface KeyStoreManager {

    fun getOrCreateCipherFor(
        keyId: CipherKeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray? = null,
    ): Result<Cipher, KeyStoreManagerError>

    fun deleteKey(keyId: KeyId)
}
