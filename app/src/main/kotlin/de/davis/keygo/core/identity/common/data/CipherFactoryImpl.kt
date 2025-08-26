package de.davis.keygo.core.identity.common.data

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.crypto.CryptographicConstants
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.domain.model.crypto.asAesKey
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.CoroutineContext

@Single
class CipherFactoryImpl : CipherFactory {

    override fun prepareCipher(
        mode: CryptographicMode,
        kek: AesKey,
        iv: ByteArray?
    ): Result<Cipher, CryptographyError> {
        val cipherMode = when (mode) {
            CryptographicMode.Wrap -> Cipher.WRAP_MODE
            CryptographicMode.Unwrap -> Cipher.UNWRAP_MODE
        }

        return runCatching {
            CryptographicConstants.DEFAULT_CIPHER.apply {
                iv?.let { init(cipherMode, kek.key, GCMParameterSpec(AUTH_TAG_LENGTH, iv)) }
                    ?: init(cipherMode, kek.key)
            }
        }.fold(
            onSuccess = {
                Result.Success(it)
            },
            onFailure = {
                when (it) {
                    is InvalidKeyException -> Result.Failure(CryptographyError.InvalidKey)
                    else -> Result.Failure(CryptographyError.Unknown(it))
                }
            }
        )

    }

    override fun generateIv(length: Int): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(length)

        random.nextBytes(iv)

        return iv
    }

    override suspend fun unwrapDataKey(
        cipher: Cipher,
        wrappedKey: ByteArray,
        coroutineContext: CoroutineContext
    ): Result<AesKey, CryptographyError> = withContext(coroutineContext) {
        runCatching {
            cipher.unwrap(
                wrappedKey,
                cipher.algorithm,
                Cipher.SECRET_KEY
            ) as SecretKey
        }.fold(
            onFailure = {
                println("Error unwrapping data key: ${it}")
                when (it) {
                    is IllegalStateException -> Result.Failure(CryptographyError.IllegalState)
                    is NoSuchAlgorithmException -> Result.Failure(CryptographyError.NoSuchAlgorithm)
                    is InvalidKeyException -> Result.Failure(CryptographyError.InvalidKey)
                    else -> Result.Failure(CryptographyError.Unknown(it))
                }
            },
            onSuccess = { Result.Success(it.asAesKey()) }
        )
    }

    override suspend fun wrapDataKey(
        cipher: Cipher,
        keyToWrap: AesKey,
        coroutineContext: CoroutineContext
    ): Result<ByteArray, CryptographyError> = withContext(coroutineContext) {
        runCatching {
            cipher.wrap(keyToWrap.key)
        }.fold(
            onFailure = {
                when (it) {
                    is IllegalStateException -> Result.Failure(CryptographyError.IllegalState)
                    is NoSuchAlgorithmException -> Result.Failure(CryptographyError.NoSuchAlgorithm)
                    is InvalidKeyException -> Result.Failure(CryptographyError.InvalidKey)
                    else -> Result.Failure(CryptographyError.Unknown(it))
                }
            },
            onSuccess = { Result.Success(it) }
        )
    }

    companion object {
        private const val AUTH_TAG_LENGTH = 128
    }
}