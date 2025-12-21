package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.security.domain.crypto.CryptographicConstants
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.CoroutineContext

internal class CryptographicScopeImpl(private val aesKey: AesKey) : CryptographicScope {

    override suspend fun ByteArray.encrypt(
        context: CoroutineContext
    ): CryptographicData = withContext(context) {
        if (isEmpty()) return@withContext CryptographicData(this@encrypt, byteArrayOf())
        val cipher = getCipher()

        cipher.init(Cipher.ENCRYPT_MODE, aesKey.key)
        val encrypted = cipher.doFinal(this@encrypt)

        CryptographicData(encrypted, cipher.iv)
    }

    override suspend fun CryptographicData.decrypt(
        context: CoroutineContext
    ): ByteArray = withContext(context) {
        if (data.isEmpty()) return@withContext data

        // If data is set, we require a valid IV
        require(iv.size == IV_LENGTH) { "Invalid IV length: ${iv.size}, expected: $IV_LENGTH" }

        val cipher = getCipher()
        cipher.init(Cipher.DECRYPT_MODE, aesKey.key, GCMParameterSpec(T_LEN, iv))

        cipher.doFinal(data)
    }

    companion object {
        const val IV_LENGTH = 12 // 96 bits
        const val T_LEN = 128

        private fun getCipher() =
            Cipher.getInstance("${CryptographicConstants.ALGORITHM}/${CryptographicConstants.BLOCK_MODE}/${CryptographicConstants.PADDING_MODE}")
    }
}