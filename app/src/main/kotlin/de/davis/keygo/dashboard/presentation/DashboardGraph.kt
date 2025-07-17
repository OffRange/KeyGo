package de.davis.keygo.dashboard.presentation

import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
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
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.ObserveAsEvents
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.dashboard.presentation.model.DashboardEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.item.create.presentation.password.PasswordScreen
import de.davis.keygo.item.viewing.presentation.password.ViewPasswordScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun NavGraphBuilder.dashboardGraph(
    listNavigator: ThreePaneScaffoldNavigator<DetailItem>
) {
    composable<RouteDestination.Home.Root> {
        val isSinglePaneMode by remember(listNavigator.scaffoldDirective) {
            derivedStateOf {
                listNavigator.scaffoldDirective.maxHorizontalPartitions == 1
            }
        }
        val viewModel = koinViewModel<DashboardViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        ObserveAsEvents(viewModel.eventFlow) {
            when (it) {
                is DashboardEvent.CreateNewItemRequest -> {
                    listNavigator.navigateTo(
                        ThreePaneScaffoldRole.Primary,
                        DetailItem.Edit(it.itemType)
                    )
                }

                is DashboardEvent.CreateOrUpdate -> {
                    listNavigator.navigateTo(
                        ThreePaneScaffoldRole.Primary,
                        DetailItem.Edit.byTotpUri(it.totpUri)
                    )
                }
            }
        }


        LaunchedEffect(uiState.openedItemId) {
            if (uiState.openedItemId != ItemIdNone)
                listNavigator.navigateTo(
                    ThreePaneScaffoldRole.Primary,
                    DetailItem.View(uiState.openedItemId)
                )
        }

        LaunchedEffect(isSinglePaneMode, listNavigator.currentDestination) {
            if (!isSinglePaneMode && uiState.openedItemId == ItemIdNone) {
                // If we are in multi pane mode and no item is opened, we request to open the first item
                viewModel.onEvent(DashboardUIEvent.OpenFirstItem)
            } else if (listNavigator.currentDestination?.contentKey == null) {
                // If the user navigates back (from the detail pane), we close the item
                viewModel.onEvent(DashboardUIEvent.CloseItem)
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
                        DashboardContent(uiState = uiState, onEvent = viewModel::onEvent)
                    }
                },
                detailPane = {
                    AnimatedPane {
                        Surface {
                            when (val detailItem = listNavigator.currentDestination?.contentKey) {
                                is DetailItem.Edit -> {
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
                                        PasswordScreen(
                                            getBy = detailItem.getBy,
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

                                is DetailItem.View -> {
                                    ViewPasswordScreen(
                                        itemId = detailItem.itemId,
                                        navigate = {
                                            when (it) {
                                                NavigationEvent.NavigateBack -> scope.launch {
                                                    listNavigator.navigateBack()
                                                }

                                                is NavigationEvent.NavigateToEdit -> scope.launch {
                                                    listNavigator.navigateTo(
                                                        ThreePaneScaffoldRole.Primary,
                                                        DetailItem.Edit.byId(
                                                            it.vaultType,
                                                            it.itemId
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                    }
                }
            )
        }
    }
}