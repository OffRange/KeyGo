package de.davis.keygo.core.data.crypto

import de.davis.keygo.core.domain.crypto.CryptographicConstants
import de.davis.keygo.core.domain.crypto.CryptographicScope
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.CoroutineContext

internal class CryptographicScopeImpl(private val aesKey: AesKey) : CryptographicScope {

    override suspend fun ByteArray.encrypt(
        context: CoroutineContext
    ): CryptographicData =
        withContext(context) {
            if (isEmpty()) return@withContext CryptographicData(this@encrypt)
            val cipher = getCipher()

            cipher.init(Cipher.ENCRYPT_MODE, aesKey.key)
            val encrypted = cipher.doFinal(this@encrypt) + cipher.iv

            CryptographicData(encrypted)
        }

    override suspend fun CryptographicData.decrypt(
        context: CoroutineContext
    ): ByteArray = withContext(context) {
        if (data.isEmpty()) return@withContext data
        val cipher = getCipher()

        val iv = data.takeLast(IV_LENGTH).toByteArray()
        val encrypted = data.dropLast(IV_LENGTH).toByteArray()

        cipher.init(Cipher.DECRYPT_MODE, aesKey.key, GCMParameterSpec(T_LEN, iv))

        cipher.doFinal(encrypted)
    }

    companion object {
        const val IV_LENGTH = 12 // 96 bits
        const val T_LEN = 128

        private fun getCipher() =
            Cipher.getInstance("${CryptographicConstants.ALGORITHM}/${CryptographicConstants.BLOCK_MODE}/${CryptographicConstants.PADDING_MODE}")
    }
}