package de.davis.keygo.feature.auth.presentation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The import travels whole: back stack keys are saved with kotlinx.serialization. */
@Serializable
data class AuthRoute(
    val uri: String? = null,
    val showBiometricPromptIfPossible: Boolean = true,
) : NavKey
