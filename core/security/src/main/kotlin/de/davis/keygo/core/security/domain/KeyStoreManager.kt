package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.model.CipherKeyId
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.model.SigningKeyId
import de.davis.keygo.core.util.Result
import java.security.PublicKey
import javax.crypto.Cipher

interface KeyStoreManager {

    fun getOrCreateCipherFor(
        keyId: CipherKeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray? = null,
    ): Result<Cipher, KeyStoreManagerError>

    fun getOrCreatePublicKey(keyId: SigningKeyId): Result<PublicKey, KeyStoreManagerError>
    fun sign(keyId: SigningKeyId, message: ByteArray): Result<ByteArray, KeyStoreManagerError>

    fun deleteKey(keyId: KeyId)
}
