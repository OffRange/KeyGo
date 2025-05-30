package de.davis.keygo.auth.data.factory

import de.davis.keygo.auth.domain.factory.BiometricCipherFactory
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.crypto.CryptographicConstants
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.domain.model.crypto.asAesKey
import kotlinx.coroutines.withContext
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.CoroutineContext

class BiometricCipherFactoryImpl : BiometricCipherFactory {

    override fun prepareCipher(
        mode: CryptographicMode,
        kek: AesKey,
        iv: ByteArray
    ): Result<Cipher, CryptographyError> {
        val cipherMode = when (mode) {
            CryptographicMode.Wrap -> Cipher.WRAP_MODE
            CryptographicMode.Unwrap -> Cipher.UNWRAP_MODE
        }

        return runCatching {
            CryptographicConstants.DEFAULT_CIPHER.apply {
                init(cipherMode, kek.key, GCMParameterSpec(AUTH_TAG_LENGTH, iv))
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

    companion object {
        private const val AUTH_TAG_LENGTH = 128
    }
}