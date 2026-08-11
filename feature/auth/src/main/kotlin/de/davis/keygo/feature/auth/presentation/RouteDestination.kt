package de.davis.keygo.feature.auth.presentation

import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.core.ui.model.PendingTotpImport
import kotlinx.serialization.Serializable

/**
 * The pending import travels as primitives, not as a [PendingTotpImport] field. Type-safe
 * navigation has no [androidx.navigation.NavType] for a custom class unless one is supplied
 * through a typeMap, and building the graph without it throws while the graph is created.
 */
@Serializable
data class AuthRoute(
    val totpInfo: String? = null,
    val queries: String? = null,
    val showBiometricPromptIfPossible: Boolean = true,
) : RouteDestination {
    val pendingTotpImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)

    val uri: String?
        get() = pendingTotpImport.uri
}
