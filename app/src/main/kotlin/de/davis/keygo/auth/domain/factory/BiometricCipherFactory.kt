package de.davis.keygo.auth.domain.factory

import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.model.crypto.AesKey
import kotlinx.coroutines.Dispatchers
import javax.crypto.Cipher
import kotlin.coroutines.CoroutineContext

interface BiometricCipherFactory {

    fun prepareCipher(
        mode: CryptographicMode,
        kek: AesKey,
        iv: ByteArray
    ): Result<Cipher, CryptographyError>

    suspend fun unwrapDataKey(
        cipher: Cipher,
        wrappedKey: ByteArray,
        coroutineContext: CoroutineContext = Dispatchers.Default
    ): Result<AesKey, CryptographyError>
}