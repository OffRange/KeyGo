package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.components.HeaderContent
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.presentation.components.FilterBottomSheet
import de.davis.keygo.feature.list_screen.presentation.components.SearchResult
import de.davis.keygo.feature.list_screen.presentation.model.Event
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import de.davis.keygo.core.ui.R as CoreUiR

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
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    val searchBarState = rememberSearchBarState()

    LaunchedEffect(autoSelectFirst) {
        if (!autoSelectFirst) viewModel.resetHighlight()
    }

    LaunchedEffect(uiState.items, uiState.highlightedId, autoSelectFirst) {
        if (autoSelectFirst && uiState.highlightedId == null && uiState.items.isNotEmpty())
            viewModel.onItemClick(uiState.items.first().id, forceSkipSelection = true)
    }

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

    val scope = rememberCoroutineScope()
    val searchInputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = viewModel.searchTextFieldState,
            searchBarState = searchBarState,
            onSearch = {
                viewModel.onSubmitQuery()
                scope.launch { searchBarState.animateToCollapsed() }
            },
            enabled = false, // TODO: remove when b/464761441 is fixed
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                AnimatedContent(
                    targetState = !uiState.hasSearchQuery && searchBarState.targetValue == SearchBarValue.Collapsed
                ) {
                    when (it) {
                        true -> Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )

                        false -> IconButton(
                            onClick = {
                                viewModel.onClearQuery()
                                scope.launch { searchBarState.animateToCollapsed() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                }
            },
            trailingIcon = {
                IconButton(onClick = { showFilterSheet = true }) {
                    BadgedBox(
                        badge = {
                            if (!filterSheetState.isDefault) Badge()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filter),
                        )
                    }
                }
            },
            placeholder = {
                Text(text = stringResource(R.string.search_your_vault))
            }
        )
    }
    if (showFilterSheet)
        FilterBottomSheet(
            state = filterSheetState,
            onAction = viewModel::onFilterAction,
            onDismiss = { showFilterSheet = false },
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
                        viewModel.onItemClick(item.id, forceSkipSelection = true)
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
                                        Text(text = stringResource(CoreUiR.string.create_new_item))
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

                            false -> Text(text = stringResource(CoreUiR.string.match_not_found))
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
                        onDelete = viewModel::onDelete,
                        onItemClick = viewModel::onItemClick,
                        onItemLongClick = viewModel::onItemLongClick,
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
private fun ItemListScreenPreview() {
    MaterialTheme {
        ItemListScreen(
            onItemClick = { },
            onItemLongClick = { },
            onCreateItemRequest = {},
        )
    }
}