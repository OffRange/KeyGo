package de.davis.keygo.core.security.presentation

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.security.data.resolve
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import java.security.Key
import javax.crypto.Cipher
import kotlin.coroutines.resume

internal class BiometricCryptoControllerImpl(
    private val activity: FragmentActivity,
    private val keyStoreManager: KeyStoreManager
) : BiometricCryptoController {

    private val biometricManager by lazy {
        BiometricManager.from(activity)
    }

    override suspend fun requestCipher(
        keyId: KeyId,
        mode: CryptographicMode,
        policy: BiometricPolicy,
    ): Result<Cipher, BiometricAuthError> = request(
        keyId = keyId,
        policy = policy,
        mode = mode
    ) { it }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy
    ): Result<Key, BiometricAuthError> = request(
        keyId = keyId,
        policy = policy,
        mode = CryptographicMode.Unwrap,
        iv = ciphertextData.iv
    ) { it.unwrap(ciphertextData.bytes, "AES", Cipher.SECRET_KEY) }

    override suspend fun requestEncryption(
        keyId: KeyId,
        byteArray: ByteArray,
        policy: BiometricPolicy
    ): Result<CiphertextData, BiometricAuthError> = request(
        keyId = keyId,
        policy = policy,
        mode = CryptographicMode.Encrypt
    ) { CiphertextData(it.doFinal(byteArray), it.iv) }

    override suspend fun requestDecryption(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy
    ): Result<ByteArray, BiometricAuthError> = request(
        keyId = keyId,
        policy = policy,
        mode = CryptographicMode.Decrypt,
        iv = ciphertextData.iv
    ) { it.doFinal(ciphertextData.bytes) }

    private suspend fun <T> request(
        keyId: KeyId,
        policy: BiometricPolicy,
        mode: CryptographicMode,
        iv: ByteArray? = null,
        onSuccess: (Cipher) -> T
    ): Result<T, BiometricAuthError> = suspendCancellableCoroutine { c ->
        when (val code = biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {}

            else -> {
                c.resume(Result.Failure(BiometricAuthError.CanNotAuthenticate(code)))
                return@suspendCancellableCoroutine
            }
        }

        val prompt = BiometricPrompt(
            activity,
            Dispatchers.Main.asExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher
                        ?.let(onSuccess)
                        .asResult<T, BiometricAuthError>(BiometricAuthError.NoCipher)
                        .let(c::resume)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    c.resume(Result.Failure(biometricAuthErrorFrom(errorCode, errString)))
                }

                override fun onAuthenticationFailed() {
                    // We do not resume, as this causes the coroutine to be finished and we cannot
                    // handle further attempts. The Android framework may still send further events,
                    // which we could handle.
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(policy.title.resolve(activity))
            .setNegativeButtonText(policy.negativeButton.resolve(activity))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        val cipher = keyStoreManager.getOrCreateCipherFor(keyId, mode, iv)
        val cryptoObj = BiometricPrompt.CryptoObject(cipher)

        prompt.authenticate(promptInfo, cryptoObj)

        c.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
    }

    companion object {
        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}

/**
 * Classify a [BiometricPrompt] error code into a semantic [BiometricAuthError] once, at the source,
 * so consumers branch on meaning instead of re-interpreting raw androidx error codes.
 */
internal fun biometricAuthErrorFrom(
    errorCode: Int,
    errString: CharSequence,
): BiometricAuthError = when (errorCode) {
    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricAuthError.Declined

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> BiometricAuthError.LockedOut

    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED,
        -> BiometricAuthError.Canceled

    else -> BiometricAuthError.Unknown(errorCode, errString.toString())
}

@Composable
fun rememberBiometricCryptoController(): BiometricCryptoController {
    val activity = LocalActivity.current as? FragmentActivity
    requireNotNull(activity) { "rememberBiometricCryptoController must be used within a FragmentActivity context." }

    val keyStoreManager = koinInject<KeyStoreManager>()

    return remember(activity, keyStoreManager) {
        BiometricCryptoControllerImpl(activity, keyStoreManager)
    }
}