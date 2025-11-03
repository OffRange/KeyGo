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
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyInfo
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun requestWrap(
        keyInfo: KeyInfo,
        key: Key,
        policy: BiometricPolicy
    ): Result<ByteArray, BiometricAuthError> = request(
        keyInfo = keyInfo,
        policy = policy,
    ) { it.wrap(key) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun requestUnwrap(
        keyInfo: KeyInfo,
        wrappedKey: ByteArray,
        policy: BiometricPolicy
    ): Result<Key, BiometricAuthError> = request(
        keyInfo = keyInfo,
        policy = policy,
    ) { it.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY) }

    private suspend fun <T> request(
        keyInfo: KeyInfo,
        policy: BiometricPolicy,
        onSuccess: (Cipher) -> T
    ): Result<T, BiometricAuthError> = suspendCancellableCoroutine { c ->
        when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                c.resume(Result.Failure(BiometricAuthError.NoHardware))
                return@suspendCancellableCoroutine
            }

            else -> {}
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
                    c.resume(
                        Result.Failure(
                            BiometricAuthError.BiometricError(
                                errorCode,
                                errString.toString()
                            )
                        )
                    )
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
            .setSubtitle("Using biometric authentication") // TODO
            .setDescription("Please authenticate to proceed")
            .setNegativeButtonText(policy.negativeButton.resolve(activity))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        val cipher = keyStoreManager.getOrCreateCipherFor(keyInfo, CryptographicMode.Wrap)
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

@Composable
fun rememberBiometricCryptoController(): BiometricCryptoController {
    val activity = LocalActivity.current as? FragmentActivity
    requireNotNull(activity) { "rememberBiometricCryptoController must be used within a FragmentActivity context." }

    val keyStoreManager = koinInject<KeyStoreManager>()

    return remember(activity, keyStoreManager) {
        BiometricCryptoControllerImpl(activity, keyStoreManager)
    }
}