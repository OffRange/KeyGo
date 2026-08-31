package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.list_screen.presentation.components.ItemListContent
import de.davis.keygo.feature.list_screen.presentation.model.Event
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf


@Stable
sealed interface NoItemStrategy {
    object ShowCreateNewItemCard : NoItemStrategy
    object ShowMessage : NoItemStrategy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onItemClick: (ItemId) -> Unit,
    onCreateItemRequest: (VaultItemType) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: (ItemId) -> Unit = {},
    onItemsDelete: (deleted: Set<ItemId>, firstItemId: ItemId?) -> Unit = { _, _ -> },
    restrictedItemType: VaultItemType? = null,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    suggestedItemIds: Set<ItemId> = emptySet(),
    openItemId: ItemId? = null,
    autoSelectFirst: Boolean = false,
    enableDeletion: Boolean = true,
    enableSelection: Boolean = true,
    dockedSearchResults: Boolean = false,
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
) {
    val viewModel = koinViewModel<ItemListViewModel> {
        parametersOf(enableSelection, restrictedItemType)
    }
    val collectedState by viewModel.listItemState.collectAsStateWithLifecycle()
    val filterSheetState by viewModel.filterBottomSheetState.collectAsStateWithLifecycle()

    // Suggestions rank the default list only. Once the user searches, the results carry their own
    // relevance and a group pinned above them would fight it.
    val suggested = if (collectedState.hasSearchQuery) emptySet() else suggestedItemIds
    val uiState = remember(collectedState, suggested) {
        collectedState.copy(items = collectedState.items.withSuggestedFirst(suggested))
    }

    // The highlight follows what the pane shows, so a dropped detail cannot leave a row marked as
    // open, and an empty pane is filled from the list. The highlight written here only reaches the
    // pane twice is what keeps the two from disagreeing within the pass.
    LaunchedEffect(uiState.items, openItemId, autoSelectFirst) {
        viewModel.setHighlight(if (autoSelectFirst) openItemId else null)

        val target = if (autoSelectFirst && openItemId == null) uiState.items.firstOrNull()?.id
        else null
        if (target != null) viewModel.onItemClick(target, forceSkipSelection = true)
    }

    val currentOnItemsDelete by rememberUpdatedState(onItemsDelete)
    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            is Event.ItemSelected -> {
                onItemClick(it.itemId)
            }

            is Event.ItemLongClicked -> {
                onItemLongClick(it.itemId)
            }

            is Event.ItemsDeleted -> {
                currentOnItemsDelete(it.itemIds, it.firstItemId)
            }
        }
    }


    val searchBarState = rememberSearchBarState()

    // In case the user types something, but does not submit the search, we rollback to the last
    // submitted search query.
    ObserveAsEvents(snapshotFlow { searchBarState.targetValue }, searchBarState) {
        when (it) {
            SearchBarValue.Collapsed -> {
                viewModel.resetToMatchSubmittedQuery()
            }

            else -> {}
        }
    }

    ItemListContent(
        uiState = uiState,
        searchBarState = searchBarState,
        searchTextFieldState = viewModel.searchTextFieldState,
        filterBottomSheetState = filterSheetState,
        dockedSearchResults = dockedSearchResults,
        enableDeletion = enableDeletion,
        autoSelectFirst = autoSelectFirst,
        notFoundStrategy = notFoundStrategy,
        restrictedItemType = restrictedItemType,
        suggestedItemIds = suggested,
        onCreateItemRequest = onCreateItemRequest,
        onSubmitQuery = viewModel::onSubmitQuery,
        onClearQuery = viewModel::onClearQuery,
        onFilterAction = viewModel::onFilterAction,
        onItemClick = viewModel::onItemClick,
        onItemLongClick = viewModel::onItemLongClick,
        onClearSelection = viewModel::onClearSelection,
        onSelectAll = viewModel::onSelectAll,
        onDeleteSelectedRequest = viewModel::onDeleteSelectedRequest,
        onPinSelectedRequest = viewModel::onPinSelectedRequest,
        onDismissDeleteConfirmation = viewModel::onDismissDeleteConfirmation,
        onConfirmDeleteSelected = viewModel::onConfirmDeleteSelected,
        scrollBehavior = scrollBehavior,
        onVaultSelectorClick = viewModel::onVaultSelectorClick,
        onDismissVaultFlow = viewModel::onDismissVaultFlow,
        modifier = modifier
    )
}
