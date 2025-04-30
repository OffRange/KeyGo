package de.davis.keygo.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.generated.RouteDestination

fun NavGraphBuilder.authNavGraph(onSuccess: () -> Unit) {
    composable<RouteDestination.Auth> {
        AuthScreen(
            navigate = onSuccess
        )
    }
}