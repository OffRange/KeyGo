package de.davis.keygo.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.generated.Route

fun NavGraphBuilder.authNavGraph(onSuccess: () -> Unit) {
    composable<Route.Auth> {
        AuthScreen(
            navigate = onSuccess
        )
    }
}