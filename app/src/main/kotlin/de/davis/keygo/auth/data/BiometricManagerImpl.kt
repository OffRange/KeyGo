package de.davis.keygo.auth.data

import android.content.Context
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import de.davis.keygo.auth.domain.BiometricManager
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.model.BiometricResult
import de.davis.keygo.auth.domain.model.BiometricStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.biometric.BiometricManager as AndroidBiometricManager

class BiometricManagerImpl(
    private val context: Context,
    private val title: String,
    private val negativeButtonText: String
) : BiometricManager() {

    private val androidBiometricManager by lazy { AndroidBiometricManager.from(context) }

    override fun getBiometricStatus(): BiometricStatus =
        when (androidBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Available
            else -> BiometricStatus.Unavailable
        }

    override suspend fun requestBiometric(biometricRequest: BiometricRequest): Flow<BiometricResult> {
        val resolverFragment = getResolverFragment()

        return callbackFlow {
            val promptInfo = generatePromptInfo(biometricRequest)

            val callback = object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationFailed() {
                    trySend(BiometricResult.Failed)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    trySend(BiometricResult.Error)
                    close()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    trySend(BiometricResult.Success)
                    close()
                }
            }

            val prompt = BiometricPrompt(
                resolverFragment,
                Runnable::run,
                callback
            )

            prompt.authenticate(promptInfo)

            awaitClose()
        }
    }

    private fun generatePromptInfo(biometricRequest: BiometricRequest): BiometricPrompt.PromptInfo =
        with(biometricRequest) {
            when (this) {
                is BiometricRequest.Class2 -> {
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK)
                        .build()
                }

                // TODO is BiometricRequest.Class3 -> throw ServiceNotSupportedException()
            }
        }

    private fun getResolverFragment(): ResolverFragment {
        val fragmentManager: FragmentManager =
            fragmentManager ?: error("can't check biometry without active window")

        val currentFragment: Fragment? = fragmentManager
            .findFragmentByTag(BIOMETRIC_RESOLVER_FRAGMENT_TAG)

        return if (currentFragment != null) {
            currentFragment as ResolverFragment
        } else {
            ResolverFragment().apply {
                fragmentManager
                    .beginTransaction()
                    .add(this, BIOMETRIC_RESOLVER_FRAGMENT_TAG)
                    .commitNow()
            }
        }
    }

    class ResolverFragment : Fragment()

    companion object {
        private const val BIOMETRIC_RESOLVER_FRAGMENT_TAG = "BiometricResolverFragment"
    }
}