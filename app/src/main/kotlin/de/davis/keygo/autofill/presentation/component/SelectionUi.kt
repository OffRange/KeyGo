package de.davis.keygo.autofill.presentation.component

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.davis.keygo.auth.presentation.authGraph
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.dashboard.presentation.dashboardGraph

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SelectionUi(onItemSelected: (ItemId) -> Unit) {
    Scaffold { innerPadding ->
        val navController = rememberNavController()
        val listPaneNavigator = rememberListDetailPaneScaffoldNavigator<DetailItem>()

        NavHost(
            navController = navController,
            startDestination = RouteDestination.Auth(),
            modifier = Modifier.Companion
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            authGraph(
                onSuccess = {
                    navController.navigate(RouteDestination.Home.Root()) {
                        popUpTo<RouteDestination.Auth> { inclusive = true }
                    }
                }
            )

            dashboardGraph(
                listNavigator = listPaneNavigator,
                onItemClicked = onItemSelected,
                autoSelect = false
            )
        }
    }
}