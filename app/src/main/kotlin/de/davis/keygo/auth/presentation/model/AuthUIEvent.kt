package de.davis.keygo.auth.presentation.model

import androidx.biometric.BiometricPrompt

sealed interface AuthUIEvent {

    data object Submit : AuthUIEvent
    data object RequestBiometricAuthentication : AuthUIEvent

    data object BiometricError : AuthUIEvent
    data object BiometricFailure : AuthUIEvent
    data class BiometricSuccess(val result: BiometricPrompt.AuthenticationResult) : AuthUIEvent
    data class ToggleUseBiometrics(val checked: Boolean) : AuthUIEvent
}