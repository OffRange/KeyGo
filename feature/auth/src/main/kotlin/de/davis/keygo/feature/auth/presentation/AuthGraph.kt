package de.davis.keygo.feature.auth.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

fun NavGraphBuilder.authGraph(onSuccess: (String?) -> Unit) {
    composable<AuthRoute> { s ->
        AuthScreen(
            onSuccess = {
                onSuccess(s.toRoute<AuthRoute>().uri)
            }
        )
    }
}
