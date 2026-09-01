package de.davis.keygo.dashboard.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.app.presentation.navigation.AppNavigator
import de.davis.keygo.app.presentation.navigation.ShellVisibility
import de.davis.keygo.app.presentation.navigation.appShell
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.create.presentation.EditVaultItemScreen
import de.davis.keygo.feature.item.view.ViewVaultItemScreen
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
fun EntryProviderScope<NavKey>.dashboardEntries(navigator: AppNavigator) {
    entry<RouteDestination.Home>(
        metadata = ListDetailSceneStrategy.listPane() + appShell(ShellVisibility.Always),
    ) {
        val listPaneVisible = !LocalIsInSinglePaneMode.current
        val openDetail = navigator.state.openDetail

        ItemListScreen(
            onItemClick = { itemId -> navigator.showDetail(RouteDestination.ViewItem(itemId)) },
            onCreateItemRequest = { type ->
                navigator.showDetail(RouteDestination.CreateItem(type))
            },
            onItemsDelete = { deleted, firstItemId ->
                val shown = navigator.state.openDetail as? RouteDestination.ViewItem
                if (shown != null && shown.id in deleted) {
                    // Beside the list the next item takes the pane; on its own it would show
                    // nothing.
                    if (listPaneVisible && firstItemId != null)
                        navigator.showDetail(RouteDestination.ViewItem(firstItemId))
                    else navigator.closeDetail()
                }
            },
            openItemId = (openDetail as? RouteDestination.ViewItem)?.id,
            // Never picks a row over a form the user may still be filling in.
            autoSelectFirst = listPaneVisible && openDetail !is RouteDestination.Form,
            dockedSearchResults = listPaneVisible,
            enableDeletion = true,
            enableSelection = true,
        )
    }

    entry<RouteDestination.ViewItem>(metadata = DetailPaneMetadata) { route ->
        ViewVaultItemScreen(
            itemId = route.id,
            navigate = { event ->
                when (event) {
                    NavigationEvent.NavigateBack -> navigator.goBack()

                    is NavigationEvent.NavigateToEdit -> navigator.openOnTopOfDetail(
                        RouteDestination.EditItem(event.vaultType, event.itemId),
                    )
                }
            },
        )
    }

    entry<RouteDestination.CreateItem>(metadata = DetailPaneMetadata) { route ->
        EditVaultItemScreen(
            detailPaneInformation = DetailPaneInformation.Init.New(route.itemType),
            onCreated = { navigator.goBack() },
            navigateBack = { navigator.goBack() },
        )
    }

    entry<RouteDestination.EditItem>(metadata = DetailPaneMetadata) { route ->
        EditVaultItemScreen(
            detailPaneInformation = DetailPaneInformation.Init.Existing(route.itemType, route.id),
            onCreated = { navigator.goBack() },
            navigateBack = { navigator.goBack() },
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val DetailPaneMetadata: Map<String, Any> =
    ListDetailSceneStrategy.detailPane() + appShell(ShellVisibility.BesideListPane)
