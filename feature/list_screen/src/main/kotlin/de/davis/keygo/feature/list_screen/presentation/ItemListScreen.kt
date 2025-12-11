package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.feature.list_screen.presentation.components.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface ItemScreenSearcher<T> {
    suspend fun search(query: String): List<T>
}

@Stable
class ItemListScreenSearchState<T> @OptIn(ExperimentalMaterial3Api::class)
internal constructor(
    val searchTextFieldState: TextFieldState,
    val searchBarState: SearchBarState,
    val searcher: ItemScreenSearcher<T>,
    private val onQuerySubmitted: (String) -> Unit = { }
) {
    var submittedQuery by mutableStateOf("")
        private set

    var items by mutableStateOf<List<T>>(emptyList())
        private set

    val searchQuery: String
        get() = searchTextFieldState.text.toString()

    internal suspend fun performSearch(query: String) {
        this.items = searcher.search(query)
    }

    internal fun clearSearch() {
        searchTextFieldState.clearText()
        submitQuery("")
    }

    internal fun submitQuery(query: String) {
        submittedQuery = query
        onQuerySubmitted(query)
    }

    internal fun resetToMatchSubmittedQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd(submittedQuery)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> rememberItemListScreenSearchState(
    searcher: ItemScreenSearcher<T>,
    onQuerySubmitted: (String) -> Unit = { },
    searchTextFieldState: TextFieldState = rememberTextFieldState(),
    searchBarState: SearchBarState = rememberSearchBarState(),
    searchDebounce: Duration = 300.milliseconds
): ItemListScreenSearchState<T> {
    val state = remember(searchTextFieldState, searchBarState, searcher, onQuerySubmitted) {
        ItemListScreenSearchState(
            searchTextFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            searcher = searcher,
            onQuerySubmitted = onQuerySubmitted
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, state) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                snapshotFlow { state.searchQuery }
                    .debounce(searchDebounce)
                    .distinctUntilChanged()
                    .collect {
                        state.performSearch(it)
                    }
            }
        }
    }

    LaunchedEffect(lifecycleOwner, state) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                snapshotFlow { state.searchBarState.targetValue }
                    .collectLatest {
                        when (it) {
                            SearchBarValue.Collapsed -> {
                                state.resetToMatchSubmittedQuery()
                            }

                            else -> {}
                        }
                    }
            }
        }
    }

    return state
}

sealed interface NoItemStrategy {
    object ShowCreateNewItemCard : NoItemStrategy
    object ShowMessage : NoItemStrategy
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemListScreen(
    items: List<LiteItem>,
    searchState: ItemListScreenSearchState<LiteItem>,
    onDelete: (ItemId) -> Unit,
    onItemClick: (ItemId) -> Unit,
    onSearchResultClick: (ItemId) -> Unit,
    onItemLongClick: (ItemId) -> Unit,
    onCreateItemRequest: (VaultItemType) -> Unit,
    modifier: Modifier = Modifier,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    enableSwipeToDelete: Boolean = true,
    dockedSearchResults: Boolean = false,
    openedItemId: ItemId? = null,
    selectedItemIds: Set<ItemId> = emptySet(),
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
) {
    val scope = rememberCoroutineScope()

    val searchInputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchState.searchTextFieldState,
            searchBarState = searchState.searchBarState,
            onSearch = {
                searchState.submitQuery(it)
                scope.launch { searchState.searchBarState.animateToCollapsed() }
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                AnimatedContent(
                    targetState = searchState.searchQuery.isEmpty() && searchState.searchBarState.targetValue == SearchBarValue.Collapsed
                ) {
                    when (it) {
                        true -> Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )

                        false -> IconButton(
                            onClick = {
                                searchState.clearSearch()
                                scope.launch { searchState.searchBarState.animateToCollapsed() }
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
                state = searchState.searchBarState,
                inputField = searchInputField,
                scrollBehavior = scrollBehavior
            )

            val searchResultContent: @Composable ColumnScope.() -> Unit = {
                val scope = rememberCoroutineScope()
                SearchResult(
                    searchResult = searchState.items,
                    idOf = { it.vaultItemId },
                    nameOf = { it.name },
                    matchedInName = { true },
                    matchedInNote = { false },
                    onClick = { item ->
                        scope.launch { searchState.searchBarState.animateToCollapsed() }
                        onSearchResultClick(item.vaultItemId)
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
            when (dockedSearchResults) {
                true -> ExpandedDockedSearchBar(
                    state = searchState.searchBarState,
                    inputField = searchInputField,
                    content = searchResultContent
                )

                false -> ExpandedFullScreenSearchBar(
                    state = searchState.searchBarState,
                    inputField = searchInputField,
                    content = searchResultContent
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = items.isEmpty(),
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
                            searchState.submittedQuery.isBlank() && notFoundStrategy is NoItemStrategy.ShowCreateNewItemCard
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

                false -> KeyGoColumn(
                    items = items.map {
                        KeyGoColumnItem(
                            header = it.name.first(),
                            title = it.name,
                            id = it.vaultItemId
                        )
                    },
                    onDelete = onDelete,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enableSwipeToDelete = enableSwipeToDelete,
                    openedItemId = openedItemId,
                    selectedItemIds = selectedItemIds
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ItemListScreenPreview() {
    MaterialTheme {
        val items = remember {
            buildList {
                repeat(20) {
                    add(
                        LitePassword(
                            vaultItemId = it.toLong(),
                            passwordId = it.toLong(),
                            name = "A_Item #$it",
                            username = "username#$it",
                            domains = emptyList(),
                        )
                    )
                }
                repeat(20) {
                    LitePassword(
                        vaultItemId = it.toLong(),
                        passwordId = it.toLong(),
                        name = "Z_Item #$it",
                        username = "username#$it",
                        domains = emptyList(),
                    )
                }
            }
        }
        ItemListScreen(
            items = items,
            searchState = rememberItemListScreenSearchState(
                searcher = { query ->
                    items.filter {
                        it.name.startsWith(
                            query,
                            ignoreCase = true
                        )
                    }
                }
            ),
            onDelete = { },
            onItemClick = { },
            onSearchResultClick = {},
            onItemLongClick = { },
            onCreateItemRequest = {},
        )
    }
}