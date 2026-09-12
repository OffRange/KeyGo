package de.davis.keygo.core.biometrics.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.biometrics.domain.BiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.security.data.keyStoreManagerErrorFrom
import de.davis.keygo.core.security.data.resolve
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.resultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.security.Key
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@Single(createdAtStart = true, binds = [BiometricCrypto::class])
internal class BiometricCryptoImpl(
    context: Context,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val keyStoreManager: KeyStoreManager,
) : BiometricCrypto, Application.ActivityLifecycleCallbacks {

    private val host = MutableStateFlow<FragmentActivity?>(null)

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity) host.update { activity }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is FragmentActivity) host.update { null }
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) = Unit
    override fun onActivityPaused(p0: Activity) = Unit
    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) = Unit
    override fun onActivityStarted(p0: Activity) = Unit
    override fun onActivityStopped(p0: Activity) = Unit

    private suspend fun awaitHost(): FragmentActivity? = withTimeoutOrNull(250.milliseconds) {
        host.filterNotNull().first { !it.isDestroyed && !it.isFinishing }
    }

    override suspend fun requestCipher(
        keyId: KeyId,
        mode: CryptographicMode,
        policy: BiometricPolicy
    ): Result<Cipher, BiometricAuthError> = request(
        keyId = keyId,
        policy = policy,
        mode = mode
    ) { it }

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

    private suspend fun <T> request(
        keyId: KeyId,
        policy: BiometricPolicy,
        mode: CryptographicMode,
        iv: ByteArray? = null,
        onSuccess: (Cipher) -> T
    ): Result<T, BiometricAuthError> = resultBinding {
        val activity = awaitHost().asResult(BiometricAuthError.NoPromptHost).bind()

        biometricAvailabilityRepository.availability()
            .asResult(BiometricAuthError.BiometricsNotAvailable)
            .bind()

        return suspendCancellableCoroutine { c ->
            val prompt = BiometricPrompt(
                activity,
                Dispatchers.Main.asExecutor(),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val cipher = result.cryptoObject?.cipher ?: return c.resume(
                            Result.Failure(BiometricAuthError.NoCipher)
                        )

                        runCatching { onSuccess(cipher) }.fold(
                            onSuccess = { c.resume(Result.Success(it)) },
                            onFailure = {
                                Log.e(
                                    TAG,
                                    "Cipher operation failed after authentication succeeded",
                                    it
                                )
                                c.resume(Result.Failure(cipherFailureToBiometricAuthError(it)))
                            },
                        )
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

            val cipher = keyStoreManager.getOrCreateCipherFor(keyId, mode, iv).onFailure {
                c.resume(Result.Failure(it.toBiometricAuthError()))
            }.getOrNull() ?: return@suspendCancellableCoroutine

            val cryptoObj = BiometricPrompt.CryptoObject(cipher)
            prompt.authenticate(promptInfo, cryptoObj)

            c.invokeOnCancellation {
                prompt.cancelAuthentication()
            }
        }
    }

    companion object {

        private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
        private const val TAG = "BiometricCryptoImpl"
    }
}

private fun cipherFailureToBiometricAuthError(throwable: Throwable): BiometricAuthError =
    keyStoreManagerErrorFrom(throwable).toBiometricAuthError()

private fun KeyStoreManagerError.toBiometricAuthError(): BiometricAuthError = when (this) {
    KeyStoreManagerError.KeyInvalidated -> BiometricAuthError.KeyInvalidated
    KeyStoreManagerError.AuthenticationRequired -> BiometricAuthError.CryptoFailed
    KeyStoreManagerError.Unknown -> BiometricAuthError.CryptoFailed
}

private fun biometricAuthErrorFrom(
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