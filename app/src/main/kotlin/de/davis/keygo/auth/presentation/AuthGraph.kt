package de.davis.keygo.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.core.presentation.model.RouteDestination

fun NavGraphBuilder.authGraph(onSuccess: () -> Unit) {
    composable<RouteDestination.Auth> {
        AuthScreen(onSuccess = onSuccess)
    }
}