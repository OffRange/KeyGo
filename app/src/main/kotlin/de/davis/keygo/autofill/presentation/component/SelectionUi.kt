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
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.dashboard.presentation.dashboardGraph
import de.davis.keygo.item.core.presentation.model.DetailType

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SelectionUi(
    onItemSelected: (ItemId) -> Unit,
    startDestination: RouteDestination = RouteDestination.Auth()
) {
    Scaffold { innerPadding ->
        val navController = rememberNavController()
        val listPaneNavigator = rememberListDetailPaneScaffoldNavigator<DetailType>()

        NavHost(
            navController = navController,
            startDestination = startDestination,
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
