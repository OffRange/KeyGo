package de.davis.keygo.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.davis.keygo.core.presentation.model.RouteDestination

fun NavGraphBuilder.authGraph(onSuccess: (String?) -> Unit) {
    composable<RouteDestination.Auth>(
        deepLinks = listOf(
            navDeepLink<RouteDestination.Auth>(basePath = "otpauth://totp") {
                uriPattern = "otpauth://totp/{totpInfo}?{queries}"
            }
        )
    ) { s ->
        AuthScreen(
            onSuccess = {
                onSuccess(s.toRoute<RouteDestination.Auth>().uri)
            }
        )
    }
}