package de.davis.keygo.feature.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute

fun NavGraphBuilder.authGraph(onSuccess: (String?) -> Unit) {
    composable<AuthRoute>(
        deepLinks = listOf(
            navDeepLink<AuthRoute>(basePath = "otpauth://totp") {
                uriPattern = "otpauth://totp/{totpInfo}?{queries}"
            }
        )
    ) { s ->
        AuthScreen(
            onSuccess = {
                onSuccess(s.toRoute<AuthRoute>().uri)
            }
        )
    }
}