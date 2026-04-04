package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.list_screen.presentation.components.SearchResult
import de.davis.keygo.feature.list_screen.presentation.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Stable
sealed interface NoItemStrategy {
    object ShowCreateNewItemCard : NoItemStrategy
    object ShowMessage : NoItemStrategy
}

@Immutable
@JvmInline
value class ItemTypeWhitelist(val allowedTypes: Array<VaultItemType>? = null) {

    companion object {
        val ALL = ItemTypeWhitelist()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onItemClick: (ItemId) -> Unit,
    onCreateItemRequest: (VaultItemType) -> Unit,
    modifier: Modifier = Modifier,
    onItemLongClick: (ItemId) -> Unit = {},
    onItemDelete: (deleted: ItemId, firstItemId: ItemId?) -> Unit = { _, _ -> },
    onFirstItemAvailable: (ItemId) -> Unit = {},
    itemTypeWhitelist: ItemTypeWhitelist = ItemTypeWhitelist.ALL,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    enableDeletion: Boolean = true,
    enableSelection: Boolean = true,
    dockedSearchResults: Boolean = false,
    highlightedId: ItemId? = null,
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
) {
    val viewModel = koinViewModel<ItemListViewModel> {
        parametersOf(enableSelection, itemTypeWhitelist)
    }
    val uiState by viewModel.listItemState.collectAsStateWithLifecycle()

    val searchBarState = rememberSearchBarState()

    LaunchedEffect(uiState.items, highlightedId) {
        if (highlightedId == null && uiState.items.isNotEmpty()) {
            onFirstItemAvailable(uiState.items.first().vaultItemId)
        }
    }

    // In case the user types something, but does not submit the search, we rollback to the last
    // submitted search query.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, searchBarState) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                snapshotFlow { searchBarState.targetValue }
                    .collectLatest {
                        when (it) {
                            SearchBarValue.Collapsed -> {
                                viewModel.resetToMatchSubmittedQuery()
                            }

                            else -> {}
                        }
                    }
            }
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
            placeholder = {
                Text(text = stringResource(R.string.search_your_vault))
            }
        )
    }

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
                    idOf = { it.vaultItemId },
                    nameOf = { it.name },
                    matchedInName = { true },
                    matchedInNote = { false },
                    onClick = { item ->
                        scope.launch { searchBarState.animateToCollapsed() }

                        // Clicking a search result should not select the item when currently
                        // other items are selected.
                        viewModel.onItemClick(item.vaultItemId, forceSkipSelection = true)
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
                .consumeWindowInsets(innerPadding)
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
                            true -> KeyGoCard(
                                title = {
                                    Text(text = stringResource(R.string.create_new_item))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                properties = KeyGoCardProperties.elevated()
                            ) {
                                VaultItemType.entries.forEach {
                                    FilledTonalButton(
                                        onClick = { onCreateItemRequest(it) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = it.presentation.first)
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
                                header = it.name.first(),
                                title = it.name,
                                id = it.vaultItemId
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
                        openedItemId = highlightedId,
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