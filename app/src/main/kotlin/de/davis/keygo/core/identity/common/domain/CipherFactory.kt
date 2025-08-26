package de.davis.keygo.core.identity.common.domain

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import kotlinx.coroutines.Dispatchers
import javax.crypto.Cipher
import kotlin.coroutines.CoroutineContext

interface CipherFactory {

    fun prepareCipher(
        mode: CryptographicMode,
        kek: AesKey,
        iv: ByteArray? = null,
    ): Result<Cipher, CryptographyError>

    fun generateIv(length: Int = 12): ByteArray

    suspend fun unwrapDataKey(
        cipher: Cipher,
        wrappedKey: ByteArray,
        coroutineContext: CoroutineContext = Dispatchers.Default
    ): Result<AesKey, CryptographyError>

    suspend fun wrapDataKey(
        cipher: Cipher,
        keyToWrap: AesKey,
        coroutineContext: CoroutineContext = Dispatchers.Default
    ): Result<ByteArray, CryptographyError>
}