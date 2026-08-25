package de.davis.keygo.feature.list_screen.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.components.HeaderContent
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import de.davis.keygo.feature.vault.presentation.VaultFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemListContent(
    uiState: ListItemState,
    searchBarState: SearchBarState,
    searchTextFieldState: TextFieldState,
    filterBottomSheetState: FilterBottomSheetState,
    dockedSearchResults: Boolean,
    enableDeletion: Boolean,
    autoSelectFirst: Boolean,
    notFoundStrategy: NoItemStrategy,
    restrictedItemType: VaultItemType?,
    suggestedItemIds: Set<ItemId>,
    onCreateItemRequest: (VaultItemType) -> Unit,
    onSubmitQuery: () -> Unit,
    onClearQuery: () -> Unit,
    onFilterAction: (FilterAction) -> Unit,
    onItemClick: (ItemId, forceSkipSelection: Boolean) -> Unit,
    onItemLongClick: (ItemId) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelectedRequest: () -> Unit,
    onPinSelectedRequest: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onConfirmDeleteSelected: () -> Unit,
    onVaultSelectorClick: () -> Unit,
    onDismissVaultFlow: () -> Unit,
    scrollBehavior: SearchBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val searchInputField = @Composable {
        ListSearchTextField(
            searchTextFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            uiState = uiState,
            onSubmitQuery = onSubmitQuery,
            onClearQuery = onClearQuery,
            onShowFilterClick = { showFilterSheet = true },
            onVaultSelectorClick = onVaultSelectorClick,
            filterBottomSheetState = filterBottomSheetState,
        )
    }

    if (showFilterSheet)
        FilterBottomSheet(
            state = filterBottomSheetState,
            onAction = onFilterAction,
            onDismiss = { showFilterSheet = false },
        )

    if (uiState.isVaultFlowVisible)
        VaultFlow(onDismiss = onDismissVaultFlow)

    if (uiState.isDeleteConfirmationVisible)
        DeleteItemsDialog(
            itemCount = uiState.selectedItemIds.size,
            onConfirm = onConfirmDeleteSelected,
            onDismiss = onDismissDeleteConfirmation,
        )

    // Leaving a selection is what back means first, ahead of leaving the screen.
    BackHandler(enabled = uiState.isSelectionActive, onBack = onClearSelection)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (uiState.isSelectionActive)
                SelectionTopBar(
                    selectedCount = uiState.selectedItemIds.size,
                    canDelete = enableDeletion,
                    allPinned = uiState.allSelectedPinned,
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onDeleteSelected = onDeleteSelectedRequest,
                    onPinSelected = onPinSelectedRequest,
                )
            else
                AppBarWithSearch(
                    state = searchBarState,
                    inputField = searchInputField,
                    scrollBehavior = scrollBehavior
                )

            val searchResultContent: @Composable ColumnScope.() -> Unit = {
                val scope = rememberCoroutineScope()
                SearchResult(
                    searchResult = uiState.searchResults,
                    idOf = { it.id },
                    nameOf = { it.name },
                    matchedInName = { true },
                    matchedInNote = { false },
                    onClick = { item ->
                        scope.launch { searchBarState.animateToCollapsed() }

                        // Clicking a search result should not select the item when currently
                        // other items are selected.
                        onItemClick(item.id, true)
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
            when (dockedSearchResults) {
                true -> ExpandedDockedSearchBar(
                    state = searchBarState,
                    inputField = searchInputField,
                    content = searchResultContent
                )

                false -> ExpandedFullScreenSearchBar(
                    state = searchBarState,
                    inputField = searchInputField,
                    content = searchResultContent
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.items.isEmpty(),
            modifier = Modifier
                .padding(innerPadding)
                .padding(top = 4.dp)
        ) { isEmpty ->
            when (isEmpty) {
                true -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val showCreateCard =
                            !uiState.hasSearchQuery && notFoundStrategy is NoItemStrategy.ShowCreateNewItemCard
                        when (showCreateCard) {
                            true -> {
                                val createTypes = remember(restrictedItemType) {
                                    restrictedItemType?.let { listOf(it) }
                                        ?: VaultItemType.entries
                                }

                                KeyGoCard(
                                    title = {
                                        Text(text = stringResource(R.string.create_new_item))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    properties = KeyGoCardProperties.elevated()
                                ) {
                                    createTypes.forEach {
                                        FilledTonalButton(
                                            onClick = { onCreateItemRequest(it) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = it.presentation.first)
                                        }
                                    }
                                }
                            }

                            false -> Text(text = stringResource(R.string.match_not_found))
                        }
                    }
                }

                false -> {
                    val items = remember(uiState.items, suggestedItemIds) {
                        uiState.items.map {
                            KeyGoColumnItem(
                                header = when {
                                    it.id in suggestedItemIds -> HeaderContent.Suggested
                                    it.pinned -> HeaderContent.Pin
                                    else -> HeaderContent.Letter(it.name.first().uppercaseChar())
                                },
                                title = it.name,
                                id = it.id,
                                itemType = it.itemType,
                            )
                        }
                    }

                    KeyGoColumn(
                        items = items,
                        onItemClick = { onItemClick(it, false) },
                        onItemLongClick = onItemLongClick,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            bottom = 96.dp,
                        ),
                        openedItemId = if (autoSelectFirst) uiState.highlightedId else null,
                        selectedItemIds = uiState.selectedItemIds
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ItemListContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val sampleItem = remember {
                LiteItemSearchResult(
                    id = newItemId(),
                    name = "Sample Item",
                    itemType = VaultItemType.Login,
                    pinned = false,
                    matchedName = true,
                    matchedNote = false,
                    matchedTag = false,
                )
            }

            val uiState = remember {
                ListItemState(
                    items = listOf(sampleItem),
                    searchResults = listOf(sampleItem),
                    hasSearchQuery = false,
                    highlightedId = null,
                )
            }
            val searchTextFieldState = rememberTextFieldState()
            val filterBottomSheetState = remember {
                FilterBottomSheetState(
                    sortDirection = SortDirection.Ascending,
                    itemSection = ItemSectionState(
                        showPinnedSwitch = false,
                        onlyPinnedChecked = false,
                        itemTypeChips = emptyList(),
                        tagChips = emptyList()
                    ),
                    passwordSection = null,
                    isDefault = true
                )
            }
            val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

            ItemListContent(
                uiState = uiState,
                searchBarState = rememberSearchBarState(),
                searchTextFieldState = searchTextFieldState,
                filterBottomSheetState = filterBottomSheetState,
                dockedSearchResults = false,
                enableDeletion = true,
                autoSelectFirst = false,
                notFoundStrategy = NoItemStrategy.ShowCreateNewItemCard,
                restrictedItemType = null,
                suggestedItemIds = emptySet(),
                onCreateItemRequest = {},
                onSubmitQuery = {},
                onClearQuery = {},
                onFilterAction = {},
                onItemClick = { _, _ -> },
                onItemLongClick = {},
                onClearSelection = {},
                onSelectAll = {},
                onDeleteSelectedRequest = {},
                onPinSelectedRequest = {},
                onDismissDeleteConfirmation = {},
                onConfirmDeleteSelected = {},
                onVaultSelectorClick = {},
                onDismissVaultFlow = {},
                scrollBehavior = scrollBehavior
            )
        }
    }
}
