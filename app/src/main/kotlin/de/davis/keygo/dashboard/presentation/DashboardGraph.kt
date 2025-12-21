package de.davis.keygo.dashboard.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.create.presentation.EditVaultItemScreen
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.rememberItemListScreenSearchState
import de.davis.keygo.item.viewing.data.ViewVaultItemScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
fun NavGraphBuilder.dashboardGraph(
    listNavigator: ThreePaneScaffoldNavigator<DetailType>,
    onItemClicked: suspend (ItemId) -> Unit = { id ->
        listNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail, DetailType.View(id))
    },
    autoSelect: Boolean = true
) {
    composable<RouteDestination.Home.Root> {
        val isSinglePaneMode by remember(listNavigator.scaffoldDirective) {
            derivedStateOf {
                listNavigator.scaffoldDirective.maxHorizontalPartitions == 1
            }
        }
        val scope = rememberCoroutineScope()

        val viewModel = koinViewModel<DashboardViewModel>()
        val listItemState by viewModel.listItemState.collectAsStateWithLifecycle()
        val searchState = rememberItemListScreenSearchState(
            searcher = viewModel::searcher,
            onQuerySubmitted = viewModel::onSearchSubmit,
        )

        LaunchedEffect(listItemState.openedItemId) {
            if (listItemState.openedItemId != null && listItemState.openedItemId != ItemIdNone)
                onItemClicked(listItemState.openedItemId!!)
        }

        LaunchedEffect(autoSelect, isSinglePaneMode, listItemState) {
            if (!autoSelect) return@LaunchedEffect

            if (isSinglePaneMode
                || listItemState.items.isEmpty()
                || listItemState.openedItemId != null
                || listItemState.selectedItemIds.isNotEmpty()
            ) return@LaunchedEffect

            // If the user is in multi pane mode and no item is opened, we request to open the first item
            viewModel.onItemClick(listItemState.items.first().vaultItemId)
        }

        LaunchedEffect(autoSelect, isSinglePaneMode) {
            if (isSinglePaneMode) {
                // This sets the content key to null, meaning the LaunchedEffect below will close the item
                listNavigator.navigateTo(ListDetailPaneScaffoldRole.List)
            }
        }

        LaunchedEffect(autoSelect, isSinglePaneMode, listNavigator.currentDestination) {
            if (!autoSelect) return@LaunchedEffect

            if (listNavigator.currentDestination?.contentKey == null) {
                // If the user navigates back (from the detail pane), we close the item
                viewModel.closeItem()
            }
        }

        val route = it.toRoute<RouteDestination.Home.Root>()
        LaunchedEffect(route) {
            route.totpUri?.let { totpUri ->
                listNavigator.navigateTo(
                    ListDetailPaneScaffoldRole.Detail,
                    DetailType.Modify.Totp(totpUri)
                )
            }
        }

        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides isSinglePaneMode,
        ) {
            NavigableListDetailPaneScaffold(
                navigator = listNavigator,
                defaultBackBehavior = BackNavigationBehavior.PopUntilContentChange,
                listPane = {
                    AnimatedPane {
                        ItemListScreen(
                            items = listItemState.items,
                            searchState = searchState,
                            onDelete = viewModel::onDelete,
                            onItemClick = viewModel::onItemClick,
                            onSearchResultClick = viewModel::onSearchResultClick,
                            onItemLongClick = viewModel::onItemLongClick,
                            onCreateItemRequest = {
                                scope.launch {
                                    listNavigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        DetailType.Modify.CreateNew(it)
                                    )
                                }
                            },
                            dockedSearchResults = !LocalIsInSinglePaneMode.current,
                            enableSwipeToDelete = true,
                            openedItemId = listItemState.openedItemId.takeIf { !isSinglePaneMode },
                            selectedItemIds = listItemState.selectedItemIds
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        when (val detailItem = listNavigator.currentDestination?.contentKey) {
                            is DetailType.View -> {
                                ViewVaultItemScreen(
                                    itemId = detailItem.itemId,
                                    navigate = {
                                        when (it) {
                                            NavigationEvent.NavigateBack -> scope.launch {
                                                listNavigator.navigateBack()
                                            }

                                            is NavigationEvent.NavigateToEdit -> scope.launch {
                                                listNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    DetailType.Modify.Edit(
                                                        it.vaultType,
                                                        it.itemId
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            is DetailType.Modify -> {
                                val store = remember { ViewModelStore() }

                                DisposableEffect(detailItem) {
                                    onDispose {
                                        store.clear()
                                    }
                                }

                                val storeOwner = remember(store) {
                                    object : ViewModelStoreOwner {
                                        override val viewModelStore: ViewModelStore = store
                                    }
                                }

                                CompositionLocalProvider(
                                    LocalViewModelStoreOwner provides storeOwner
                                ) {
                                    EditVaultItemScreen(
                                        detailPaneInformation = detailItem.asDetailPaneInformation(),
                                        navigate = {
                                            when (it) {
                                                NavigationEvent.NavigateBack -> scope.launch {
                                                    // We don't want to pop the detail pane entirely,
                                                    // Just until the content changes
                                                    listNavigator.navigateBack(
                                                        BackNavigationBehavior.PopUntilContentChange
                                                    )
                                                }

                                                is NavigationEvent.NavigateToEdit -> {}
                                            }
                                        }
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            )
        }
    }
}