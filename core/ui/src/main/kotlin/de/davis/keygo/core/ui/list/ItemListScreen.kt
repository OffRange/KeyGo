package de.davis.keygo.core.ui.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoColumn
import de.davis.keygo.core.ui.components.KeyGoColumnItem
import de.davis.keygo.core.ui.components.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
    val searcher: ItemScreenSearcher<T>
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set

    val searchQuery: String
        get() = searchTextFieldState.text.toString()

    internal suspend fun performSearch(query: String) {
        this.items = searcher.search(query)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> rememberItemListScreenSearchState(
    searcher: ItemScreenSearcher<T>,
    searchTextFieldState: TextFieldState = rememberTextFieldState(),
    searchBarState: SearchBarState = rememberSearchBarState(),
    searchDebounce: Duration = 300.milliseconds
): ItemListScreenSearchState<T> {
    val state = remember(searchTextFieldState, searchBarState, searcher) {
        ItemListScreenSearchState(
            searchTextFieldState = searchTextFieldState,
            searchBarState = searchBarState,
            searcher = searcher
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

    return state
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <SR, ID : Any> ItemListScreen(
    items: List<KeyGoColumnItem<ID>>,
    searchState: ItemListScreenSearchState<SR>,
    idOfSearchResult: (SR) -> ID,
    titleOfSearchResult: (SR) -> String,
    onDelete: (ID) -> Unit,
    onItemClick: (ID) -> Unit,
    onSearchResultClick: (ID) -> Unit,
    onItemLongClick: (ID) -> Unit,
    noItemsSuggestions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dockedSearchResults: Boolean = false,
    openedItemId: ID? = null,
    selectedItemIds: Set<ID> = emptySet(),
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
) {
    val scope = rememberCoroutineScope()

    val searchInputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchState.searchTextFieldState,
            searchBarState = searchState.searchBarState,
            onSearch = {
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
                    idOf = { idOfSearchResult(it) },
                    nameOf = titleOfSearchResult,
                    matchedInName = { true },
                    matchedInNote = { false },
                    onClick = { item ->
                        scope.launch { searchState.searchBarState.animateToCollapsed() }
                        onSearchResultClick(idOfSearchResult(item))
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
                        KeyGoCard(
                            title = {
                                Text(text = stringResource(R.string.create_new_item))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            properties = KeyGoCardProperties.elevated()
                        ) {
                            noItemsSuggestions()
                        }
                    }
                }

                false -> KeyGoColumn(
                    items = items,
                    onDelete = onDelete,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
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
                        KeyGoColumnItem(
                            title = "Item #$it",
                            header = 'I',
                            id = size
                        )
                    )
                }
                repeat(20) {
                    KeyGoColumnItem(
                        title = "Ztem #$it",
                        header = 'Z',
                        id = size
                    )
                }
            }
        }
        ItemListScreen(
            items = items,
            searchState = rememberItemListScreenSearchState(
                searcher = { query ->
                    items.filter {
                        it.title.startsWith(
                            query,
                            ignoreCase = true
                        )
                    }
                }
            ),
            idOfSearchResult = { it.id },
            titleOfSearchResult = { it.title },
            onDelete = { },
            onItemClick = { },
            onSearchResultClick = {},
            onItemLongClick = { },
            noItemsSuggestions = {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Password")
                }
            }
        )
    }
}