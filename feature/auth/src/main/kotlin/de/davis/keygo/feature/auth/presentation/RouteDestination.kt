package de.davis.keygo.feature.auth.presentation

import de.davis.keygo.core.ui.RouteDestination
import kotlinx.serialization.Serializable

@Serializable
data class AuthRoute(
    val totpInfo: String? = null,
    val queries: String? = null,
    val showBiometricPromptIfPossible: Boolean = true
) : RouteDestination {
    val uri
        get() = if (!totpInfo.isNullOrBlank() && !queries.isNullOrBlank())
            "otpauth://totp/$totpInfo?$queries"
        else null
}