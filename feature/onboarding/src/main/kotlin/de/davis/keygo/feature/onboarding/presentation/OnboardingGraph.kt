package de.davis.keygo.feature.onboarding.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.davis.keygo.core.ui.RouteDestination
import kotlinx.serialization.Serializable


fun NavGraphBuilder.onboardingGraph(onSuccess: (String?) -> Unit) {
    composable<OnboardingRoute>(
        deepLinks = listOf(
            navDeepLink<OnboardingRoute>(basePath = "otpauth://totp") {
                uriPattern = "otpauth://totp/{totpInfo}?{queries}"
            }
        )
    ) { s ->
        OnboardingScreen(
            onSuccess = {
                onSuccess(s.toRoute<OnboardingRoute>().uri)
            }
        )
    }
}

@Serializable
data class OnboardingRoute(
    val totpInfo: String? = null,
    val queries: String? = null,
) : RouteDestination {
    val uri
        get() = if (!totpInfo.isNullOrBlank() && !queries.isNullOrBlank())
            "otpauth://totp/$totpInfo?$queries"
        else null
}