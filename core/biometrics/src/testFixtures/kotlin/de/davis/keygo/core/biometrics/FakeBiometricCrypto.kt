package de.davis.keygo.core.biometrics

import de.davis.keygo.core.biometrics.domain.BiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CompletableDeferred
import java.security.Key
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey

class FakeBiometricCrypto(
    val keyStoreManager: KeyStoreManager = FakeKeyStoreManager(),
) : BiometricCrypto {

    data class Prompt(
        val keyId: KeyId,
        val mode: CryptographicMode,
        val policy: BiometricPolicy,
    )

    var promptFailure: BiometricAuthError? = null

    var pendingPrompt: CompletableDeferred<Unit>? = null

    val prompts: MutableList<Prompt> = mutableListOf()

    val unwrapped: MutableList<ByteArray> = mutableListOf()

    override suspend fun requestWrap(
        keyId: KeyId,
        key: ByteArray,
        policy: BiometricPolicy,
    ): Result<CryptographicData, BiometricAuthError> =
        prompt(keyId, CryptographicMode.Wrap, policy, iv = null) { cipher ->
            CryptographicData(data = cipher.doFinal(key), iv = cipher.iv)
        }

    override suspend fun requestUnwrap(
        keyId: KeyId,
        cryptographicData: CryptographicData,
        policy: BiometricPolicy,
    ): Result<Key, BiometricAuthError> =
        prompt(keyId, CryptographicMode.Unwrap, policy, iv = cryptographicData.iv) { cipher ->
            val material = cipher.doFinal(cryptographicData.data)
            unwrapped += material
            HandedOutKey(material)
        }

    private suspend fun <T> prompt(
        keyId: KeyId,
        mode: CryptographicMode,
        policy: BiometricPolicy,
        iv: ByteArray?,
        onAuthenticated: (Cipher) -> T,
    ): Result<T, BiometricAuthError> {
        prompts += Prompt(keyId, mode, policy)
        pendingPrompt?.await()
        promptFailure?.let { return Result.Failure(it) }

        val cipher = when (val result = keyStoreManager.getOrCreateCipherFor(keyId, mode, iv)) {
            is Result.Success -> result.success
            is Result.Failure -> return Result.Failure(result.error.toBiometricAuthError())
        }

        return runCatching { onAuthenticated(cipher) }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it.toBiometricAuthError()) },
        )
    }

    private fun KeyStoreManagerError.toBiometricAuthError(): BiometricAuthError = when (this) {
        KeyStoreManagerError.KeyInvalidated -> BiometricAuthError.KeyInvalidated
        KeyStoreManagerError.AuthenticationRequired,
        KeyStoreManagerError.Unknown,
            -> BiometricAuthError.CryptoFailed
    }

    private fun Throwable.toBiometricAuthError(): BiometricAuthError =
        if (generateSequence(this) { it.cause }.any { it is AEADBadTagException })
            BiometricAuthError.KeyInvalidated
        else BiometricAuthError.CryptoFailed

    private class HandedOutKey(private val material: ByteArray) : SecretKey {
        override fun getAlgorithm(): String = "AES"
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = material
    }
}
