package de.davis.keygo.auth.presentation

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.R
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.core.presentation.ObserveAsEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow

@Composable
fun BiometricAuthHandler(
    flow: Flow<BiometricRequest>,
    onAuthenticationSucceeded: (BiometricPrompt.AuthenticationResult) -> Unit,
    onAuthenticationError: (Int, CharSequence) -> Unit,
    onAuthenticationFailed: () -> Unit
) {
    val fragmentActivity = LocalActivity.current as? FragmentActivity
    requireNotNull(fragmentActivity) { "BiometricAuthHandler must be used within a FragmentActivity context." }

    val prompt = remember(fragmentActivity) {
        BiometricPrompt(
            fragmentActivity,
            Dispatchers.Default.asExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticationSucceeded(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    onAuthenticationFailed()
                }
            }
        )
    }

    val title = stringResource(R.string.authenticate)
    val negativeButtonText = stringResource(R.string.cancel)

    ObserveAsEvents(flow = flow) { request ->
        when (request) {
            BiometricRequest.Class2 -> {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build()
                )
            }

            is BiometricRequest.Class3 -> {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build(),
                    BiometricPrompt.CryptoObject(request.cipher)
                )
            }
        }
    }
}