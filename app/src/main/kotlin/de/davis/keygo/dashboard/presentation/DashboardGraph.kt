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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.create.presentation.EditVaultItemScreen
import de.davis.keygo.feature.item.view.ViewVaultItemScreen
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
fun NavGraphBuilder.dashboardGraph(
    listNavigator: ThreePaneScaffoldNavigator<DetailType>,
) {
    composable<RouteDestination.Home.Root> {
        val isSinglePaneMode by remember(listNavigator.scaffoldDirective) {
            derivedStateOf {
                listNavigator.scaffoldDirective.maxHorizontalPartitions == 1
            }
        }
        val scope = rememberCoroutineScope()

        LaunchedEffect(isSinglePaneMode) {
            if (isSinglePaneMode && listNavigator.canNavigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)) {
                listNavigator.navigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)
            }
        }

        val openedItemId by remember(listNavigator) {
            derivedStateOf {
                (listNavigator.currentDestination?.contentKey as? DetailType.View)?.itemId
            }
        }

        val isModifyScreenActive by remember(listNavigator) {
            derivedStateOf {
                listNavigator.currentDestination?.contentKey is DetailType.Modify
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
                defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
                listPane = {
                    AnimatedPane {
                        ItemListScreen(
                            onItemClick = { id ->
                                scope.launch {
                                    listNavigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        DetailType.View(id)
                                    )
                                }
                            },
                            onItemDelete = { deleted, firstItemId ->
                                if (openedItemId == deleted) {
                                    scope.launch {
                                        if (!isSinglePaneMode && !isModifyScreenActive)
                                            firstItemId?.let {
                                                listNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    DetailType.View(firstItemId)
                                                )
                                            }
                                            // Navigate back if there is no item, so the deleted item's content is not being shown in the detail pane
                                                ?: listNavigator.navigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)
                                    }
                                }
                            },
                            onCreateItemRequest = {
                                scope.launch {
                                    listNavigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        DetailType.Modify.CreateNew(it)
                                    )
                                }
                            },
                            onFirstItemAvailable = { id ->
                                if (!isSinglePaneMode && !isModifyScreenActive && openedItemId == null)
                                    scope.launch {
                                        listNavigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            DetailType.View(id)
                                        )
                                    }
                            },
                            dockedSearchResults = !LocalIsInSinglePaneMode.current,
                            enableDeletion = true,
                            enableSelection = true,
                            highlightedId = openedItemId
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        when (val detailItem = listNavigator.currentDestination?.contentKey) {
                            is DetailType.View -> {
                                ViewVaultItemScreen(
                                    itemId = detailItem.itemId,
                                    navigate = { event ->
                                        when (event) {
                                            NavigationEvent.NavigateBack -> scope.launch {
                                                listNavigator.navigateBack()
                                            }

                                            is NavigationEvent.NavigateToEdit -> scope.launch {
                                                listNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    DetailType.Modify.Edit(
                                                        event.vaultType,
                                                        event.itemId
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
                                        onCreated = {
                                            scope.launch {
                                                // We don't want to pop the detail pane entirely,
                                                // Just until the content changes
                                                listNavigator.navigateBack(
                                                    BackNavigationBehavior.PopUntilContentChange
                                                )
                                            }
                                        },
                                        navigateBack = {
                                            scope.launch {
                                                listNavigator.navigateBack(
                                                    BackNavigationBehavior.PopUntilContentChange
                                                )
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