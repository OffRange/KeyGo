package de.davis.keygo.core.data.crypto

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
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

    override suspend fun ByteArray.hash(): CryptographicData {
        // TODO Support multiple hashing algorithms
        // TODO Discontinue BCrypt and use a more secure hashing algorithm -> Argon2
        return CryptographicData(BCRYPT_HASHER.hash(IV_LENGTH, this))
    }

    override suspend fun CryptographicData.checkHash(plainText: ByteArray): Boolean =
        BCrypt.verifyer(
            BCrypt.Version.VERSION_2A,
            LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
        ).verify(plainText, data).verified

    companion object {
        const val IV_LENGTH = 12 // 96 bits
        const val T_LEN = 128

        private val BCRYPT_HASHER: BCrypt.Hasher =
            BCrypt.with(LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A))

        private fun getCipher() =
            Cipher.getInstance("${CryptographicConstants.ALGORITHM}/${CryptographicConstants.BLOCK_MODE}/${CryptographicConstants.PADDING_MODE}")
    }
}