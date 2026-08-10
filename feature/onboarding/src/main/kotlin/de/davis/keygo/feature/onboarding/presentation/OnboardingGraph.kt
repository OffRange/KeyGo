package de.davis.keygo.feature.onboarding.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.core.ui.model.PendingTotpImport
import kotlinx.serialization.Serializable


fun NavGraphBuilder.onboardingGraph(onSuccess: (String?) -> Unit) {
    composable<OnboardingRoute> { s ->
        OnboardingScreen(
            onSuccess = {
                onSuccess(s.toRoute<OnboardingRoute>().uri)
            }
        )
    }
}

/**
 * The pending import travels as primitives, not as a [PendingTotpImport] field. Type-safe
 * navigation has no [androidx.navigation.NavType] for a custom class unless one is supplied
 * through a typeMap, and building the graph without it throws while the graph is created.
 */
@Serializable
data class OnboardingRoute(
    val totpInfo: String? = null,
    val queries: String? = null,
) : RouteDestination {
    val pendingTotpImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)

    val uri: String?
        get() = pendingTotpImport.uri
}
