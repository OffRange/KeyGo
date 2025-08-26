package de.davis.keygo.core.identity.biometric.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.core.domain.onFailure
import de.davis.keygo.core.domain.onSuccess
import de.davis.keygo.core.domain.usecase.HasValidAccessUseCase
import de.davis.keygo.core.identity.biometric.domain.model.BiometricAvailability
import de.davis.keygo.core.identity.biometric.domain.model.BiometricEvent
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricCryptoSetupAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricHardwareAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.biometric.presentation.model.BiometricRequest
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.presentation.UIText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BiometricViewModel(
    private val getBiometricCryptoSetupAvailability: GetBiometricCryptoSetupAvailabilityUseCase,
    private val getBiometricHardwareAvailability: GetBiometricHardwareAvailabilityUseCase,
    private val hasValidAccess: HasValidAccessUseCase,
    private val prepareBiometricCipher: PrepareBiometricCipherUseCase,
    protected val unlockWithBiometrics: UnlockWithBiometricsUseCase,
) : ViewModel() {

    private val biometricRequestChannel = Channel<BiometricRequest>()
    val biometricRequests = biometricRequestChannel.receiveAsFlow()

    fun requestBiometricAuthentication(
        mode: CryptographicMode = CryptographicMode.Unwrap,
        title: UIText = UIText.ResourceString(R.string.authenticate),
        negativeButton: UIText = UIText.ResourceString(R.string.cancel)
    ) {
        viewModelScope.launch {
            val hasAccess = hasValidAccess()

            val isBiometricHardwareAvailable =
                getBiometricHardwareAvailability() == BiometricAvailability.Available
            val isBiometricCryptoSetupAvailable = if (hasAccess)
                getBiometricCryptoSetupAvailability() == BiometricAvailability.Available
            else false

            val biometricsUsable = isBiometricHardwareAvailable && isBiometricCryptoSetupAvailable

            if (!biometricsUsable) return@launch

            prepareBiometricCipher(mode = mode).onSuccess {
                biometricRequestChannel.send(
                    BiometricRequest.Class3(
                        title = title,
                        negativeButtonText = negativeButton,
                        cipher = it
                    )
                )
            }.onFailure {
                Log.e(TAG, "Failed to prepare biometric cipher.")
            }
        }
    }

    fun onBiometricResult(result: BiometricEvent) {
        when (result) {
            is BiometricEvent.OnAuthenticationError -> {}
            is BiometricEvent.OnAuthenticationSucceeded -> onBiometricSucceeded(result)
        }
    }

    protected open fun onBiometricSucceeded(event: BiometricEvent.OnAuthenticationSucceeded) {
        val cipher = event.cipher ?: return
        viewModelScope.launch {
            unlockWithBiometrics(cipher).onSuccess {
                onUnlocked()
            }.onFailure {
                Log.e(TAG, "Failed to unlock with biometrics.")
            }

        }
    }

    abstract fun onUnlocked()

    companion object {
        private const val TAG = "BiometricViewModel"
    }
}