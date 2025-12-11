package de.davis.keygo.core.identity.biometric.presentation

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.identity.biometric.domain.model.BiometricEvent
import de.davis.keygo.core.identity.biometric.presentation.model.BiometricRequest
import de.davis.keygo.core.presentation.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Deprecated("Migrate to :core:security")
class BiometricManager(private val activity: FragmentActivity) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun authenticate(request: BiometricRequest) = suspendCancellableCoroutine { c ->
        val prompt = BiometricPrompt(
            activity,
            Dispatchers.Main.asExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    c.resume(
                        BiometricEvent.OnAuthenticationSucceeded(
                            cipher = result.cryptoObject?.cipher
                        )
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    c.resume(
                        BiometricEvent.OnAuthenticationError(
                            errorCode,
                            errString.toString()
                        )
                    )
                }

                override fun onAuthenticationFailed() {
                    // We do not resume, as this causes the coroutine to be finished and we cannot
                    // handle further attempts. The Android framework may still send further events,
                    // which we could handle .
                }
            }
        )

        when (request) {
            is BiometricRequest.Class3 -> prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(request.title.resolve(activity))
                    .setNegativeButtonText(request.negativeButtonText.resolve(activity))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build(),
                BiometricPrompt.CryptoObject(request.cipher)
            )
        }

        c.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
    }
}