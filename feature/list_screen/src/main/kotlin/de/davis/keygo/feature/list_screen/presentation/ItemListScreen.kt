package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    onItemDelete: (deleted: ItemId, firstItemId: ItemId?) -> Unit = { _, _ -> },
    restrictedItemType: VaultItemType? = null,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    autoSelectFirst: Boolean = false,
    enableDeletion: Boolean = true,
    enableSelection: Boolean = true,
    dockedSearchResults: Boolean = false,
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
) {
    val viewModel = koinViewModel<ItemListViewModel> {
        parametersOf(enableSelection, restrictedItemType)
    }
    val uiState by viewModel.listItemState.collectAsStateWithLifecycle()
    val filterSheetState by viewModel.filterBottomSheetState.collectAsStateWithLifecycle()

    LaunchedEffect(autoSelectFirst) {
        if (!autoSelectFirst) viewModel.resetHighlight()
    }

    LaunchedEffect(uiState.items, uiState.highlightedId, autoSelectFirst) {
        if (autoSelectFirst && uiState.highlightedId == null && uiState.items.isNotEmpty())
            viewModel.onItemClick(uiState.items.first().id, forceSkipSelection = true)
    }

    val currentOnItemDelete by rememberUpdatedState(onItemDelete)
    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            is Event.ItemSelected -> {
                onItemClick(it.itemId)
            }

            is Event.ItemLongClicked -> {
                onItemLongClick(it.itemId)
            }

            is Event.ItemDeleted -> {
                currentOnItemDelete(it.itemId, it.firstItemId)
            }
        }
    }

    ItemListContent(
        uiState = uiState,
        searchTextFieldState = viewModel.searchTextFieldState,
        filterBottomSheetState = filterSheetState,
        dockedSearchResults = dockedSearchResults,
        enableDeletion = enableDeletion,
        autoSelectFirst = autoSelectFirst,
        notFoundStrategy = notFoundStrategy,
        restrictedItemType = restrictedItemType,
        onCreateItemRequest = onCreateItemRequest,
        resetToMatchSubmittedQuery = viewModel::resetToMatchSubmittedQuery,
        onSubmitQuery = viewModel::onSubmitQuery,
        onClearQuery = viewModel::onClearQuery,
        onFilterAction = viewModel::onFilterAction,
        onItemClick = viewModel::onItemClick,
        onItemLongClick = viewModel::onItemLongClick,
        onDelete = viewModel::onDelete,
        scrollBehavior = scrollBehavior,
        onVaultSelect = viewModel::onVaultSelected,
        onCreateVaultRequest = viewModel::onCreateVaultRequest,
        modifier = modifier
    )
}
