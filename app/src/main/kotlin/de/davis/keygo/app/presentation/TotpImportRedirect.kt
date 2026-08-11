package de.davis.keygo.app.presentation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import kotlinx.serialization.Serializable

@Serializable
data class TotpImportRedirect(
    val totpInfo: String? = null,
    val queries: String? = null,
) : RouteDestination {
    val pendingImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)
}

fun NavGraphBuilder.totpImportRedirectGraph(
    hasAccess: Boolean,
    navigateAndReplace: (Any) -> Unit,
) {
    composable<TotpImportRedirect>(
        deepLinks = listOf(
            navDeepLink<TotpImportRedirect>(basePath = PendingTotpImport.BASE_PATH) {
                uriPattern = PendingTotpImport.URI_PATTERN
            }
        )
    ) { entry ->
        val route = entry.toRoute<TotpImportRedirect>()
        LaunchedEffect(route) {
            navigateAndReplace(
                if (hasAccess) AuthRoute(totpInfo = route.totpInfo, queries = route.queries)
                else OnboardingRoute(totpInfo = route.totpInfo, queries = route.queries)
            )
        }
    }
}
