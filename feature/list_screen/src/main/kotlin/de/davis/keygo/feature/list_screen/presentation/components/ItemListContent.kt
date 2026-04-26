package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.SearchBarValue
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.components.HeaderContent
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.list_screen.presentation.model.CreateVaultRequest
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemListContent(
    uiState: ListItemState,
    searchTextFieldState: TextFieldState,
    filterBottomSheetState: FilterBottomSheetState,
    dockedSearchResults: Boolean,
    enableDeletion: Boolean,
    autoSelectFirst: Boolean,
    notFoundStrategy: NoItemStrategy,
    restrictedItemType: VaultItemType?,
    onCreateItemRequest: (VaultItemType) -> Unit,
    resetToMatchSubmittedQuery: () -> Unit,
    onSubmitQuery: () -> Unit,
    onClearQuery: () -> Unit,
    onFilterAction: (FilterAction) -> Unit,
    onItemClick: (ItemId, forceSkipSelection: Boolean) -> Unit,
    onItemLongClick: (ItemId) -> Unit,
    onDelete: (ItemId) -> Unit,
    onVaultSelect: (SelectedVault) -> Unit,
    onCreateVaultRequest: (CreateVaultRequest) -> Unit,
    scrollBehavior: SearchBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showVaultSheet by rememberSaveable { mutableStateOf(false) }
    var showCreation by rememberSaveable { mutableStateOf(false) }
    val searchBarState = rememberSearchBarState()

    // In case the user types something, but does not submit the search, we rollback to the last
    // submitted search query.
    ObserveAsEvents(snapshotFlow { searchBarState.targetValue }, searchBarState) {
        when (it) {
            SearchBarValue.Collapsed -> {
                resetToMatchSubmittedQuery()
            }

            else -> {}
        }
    }

    val searchInputField = @Composable {
        ListSearchTextField(
            searchTextFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            uiState = uiState,
            onSubmitQuery = onSubmitQuery,
            onClearQuery = onClearQuery,
            onShowFilterClick = { showFilterSheet = true },
            onVaultSelectorClick = { showVaultSheet = true },
            filterBottomSheetState = filterBottomSheetState,
        )
    }

    if (showFilterSheet)
        FilterBottomSheet(
            state = filterBottomSheetState,
            onAction = onFilterAction,
            onDismiss = { showFilterSheet = false },
        )

    if (showVaultSheet)
        VaultSelectionSheet(
            uiState = uiState,
            onDismiss = { showVaultSheet = false },
            onVaultSelected = onVaultSelect,
            onCreateVaultRequest = {
                showVaultSheet = false
                showCreation = true
            }
        )

    if (showCreation)
        VaultCreationDialog(
            onDismissRequest = { showCreation = false },
            onCreate = {
                showCreation = false
                onCreateVaultRequest(it)
            }
        )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
                    val items = remember(uiState.items) {
                        uiState.items.map {
                            KeyGoColumnItem(
                                header = if (it.pinned) HeaderContent.Pin
                                else HeaderContent.Letter(it.name.first().uppercaseChar()),
                                title = it.name,
                                id = it.id,
                                itemType = it.itemType,
                            )
                        }
                    }

                    KeyGoColumn(
                        items = items,
                        onDelete = onDelete,
                        onItemClick = { onItemClick(it, false) },
                        onItemLongClick = onItemLongClick,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        enableSwipeToDelete = enableDeletion,
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
                    itemType = VaultItemType.Password,
                    pinned = false,
                    matchedName = true,
                    matchedNote = false,
                )
            }

            val vaults = remember {
                listOf(
                    VaultMetadata(
                        vaultId = newVaultId(),
                        name = "Vault 1",
                        icon = Vault.Icon.Default
                    ),
                    VaultMetadata(
                        vaultId = newVaultId(),
                        name = "Vault 2",
                        icon = Vault.Icon.Work
                    )
                )
            }

            var uiState by remember {
                mutableStateOf(
                    ListItemState(
                        items = listOf(sampleItem),
                        searchResults = listOf(sampleItem),
                        hasSearchQuery = false,
                        highlightedId = null,
                        selectedItemIds = emptySet(),
                        vaults = vaults,
                        selectedVault = SelectedVault.Id(vaults.first().vaultId)
                    )
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
                        labelChips = emptyList()
                    ),
                    passwordSection = null,
                    isDefault = true
                )
            }
            val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

            ItemListContent(
                uiState = uiState,
                searchTextFieldState = searchTextFieldState,
                filterBottomSheetState = filterBottomSheetState,
                dockedSearchResults = false,
                enableDeletion = true,
                autoSelectFirst = false,
                notFoundStrategy = NoItemStrategy.ShowCreateNewItemCard,
                restrictedItemType = null,
                onCreateItemRequest = {},
                resetToMatchSubmittedQuery = {},
                onSubmitQuery = {},
                onClearQuery = {},
                onFilterAction = {},
                onItemClick = { _, _ -> },
                onItemLongClick = {},
                onDelete = {},
                onVaultSelect = { uiState = uiState.copy(selectedVault = it) },
                onCreateVaultRequest = {},
                scrollBehavior = scrollBehavior
            )
        }
    }
}
