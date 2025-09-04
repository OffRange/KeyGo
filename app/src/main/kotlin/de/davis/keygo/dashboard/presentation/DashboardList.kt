package de.davis.keygo.dashboard.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass
import de.davis.keygo.R
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.component.KeyGoCard
import de.davis.keygo.core.presentation.component.KeyGoCardProp
import de.davis.keygo.dashboard.presentation.component.KeyGoLazyColumn
import de.davis.keygo.dashboard.presentation.component.SearchResult
import de.davis.keygo.dashboard.presentation.model.DashboardListUIState
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.generated.item.VaultItemType
import de.davis.keygo.generated.item.getString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardList(uiState: DashboardListUIState, onEvent: (DashboardUIEvent) -> Unit) {
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    val searchBarState = rememberSearchBarState()
    LaunchedEffect(Unit) {
        snapshotFlow { searchBarState.targetValue }
            .collectLatest {
                when (it) {
                    SearchBarValue.Collapsed -> {
                        onEvent(DashboardUIEvent.OnSearchCollapse)
                    }

                    else -> {}
                }
            }
    }

    val scope = rememberCoroutineScope()

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSize = with(LocalDensity.current) {
        currentWindowSize().toSize().toDpSize()
    }

    val shouldBeDocked = when {
        adaptiveInfo.windowPosture.isTabletop -> false

        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
                windowSize.width >= 1200.dp -> true

        adaptiveInfo.windowSizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
        ) -> true

        else -> false
    }

    val viewConfig = LocalViewConfiguration.current
    val newViewConfiguration = remember(viewConfig) {
        object : ViewConfiguration by viewConfig {
            override val touchSlop: Float
                get() = viewConfig.touchSlop * 3f
        }
    }

    val searchInputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = uiState.textFieldState,
            searchBarState = searchBarState,
            onSearch = {
                onEvent(DashboardUIEvent.OnSearchSubmit)
                scope.launch { searchBarState.animateToCollapsed() }
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                AnimatedContent(uiState.textFieldState.text.isEmpty() && searchBarState.targetValue == SearchBarValue.Collapsed) {
                    when (it) {
                        true -> Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_content_description),
                        )

                        else -> IconButton(
                            onClick = {
                                scope.launch { searchBarState.animateToCollapsed() }
                                onEvent(DashboardUIEvent.OnSearchClear)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_content_description)
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopSearchBar(
                state = searchBarState,
                inputField = searchInputField,
                scrollBehavior = scrollBehavior,
            )

            if (shouldBeDocked) {
                ExpandedDockedSearchBar(
                    state = searchBarState,
                    inputField = searchInputField,
                ) {
                    DashboardSearchResult(
                        searchResult = uiState.searchResult,
                        onCollapse = searchBarState::animateToCollapsed,
                        onEvent = onEvent
                    )
                }
            } else {
                ExpandedFullScreenSearchBar(
                    state = searchBarState,
                    inputField = searchInputField,
                ) {
                    DashboardSearchResult(
                        searchResult = uiState.searchResult,
                        onCollapse = searchBarState::animateToCollapsed,
                        onEvent = onEvent
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = uiState.items.isEmpty()
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
                                prop = KeyGoCardProp.elevated()
                            ) {
                                VaultItemType.entries.forEach {
                                    FilledTonalButton(
                                        onClick = {
                                            onEvent(DashboardUIEvent.OnCreateNewItemRequest(it))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = it.getString())
                                    }
                                }
                            }
                        }
                    }

                    else -> CompositionLocalProvider(
                        LocalViewConfiguration provides newViewConfiguration,
                    ) {
                        KeyGoLazyColumn(
                            items = uiState.items,
                            selectedItemIds = uiState.selectedItemIds,
                            openedItemId = if (LocalIsInSinglePaneMode.current) ItemIdNone else uiState.openedItemId,
                            onDeleteRequest = {
                                onEvent(DashboardUIEvent.OnDeleteRequest(it))
                            },
                            onItemClick = {
                                onEvent(DashboardUIEvent.OnOpenOrSelect(it))
                            },
                            onItemLongClick = {
                                onEvent(DashboardUIEvent.OnLongClick(it))
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSearchResult(
    searchResult: ImmutableList<VaultSearchResult>,
    onCollapse: suspend () -> Unit,
    onEvent: (DashboardUIEvent) -> Unit
) {
    val scope = rememberCoroutineScope()
    SearchResult(
        searchResult = searchResult,
        onClick = {
            scope.launch { onCollapse() }
            onEvent(DashboardUIEvent.OnOpen(it.vaultItemId))
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun PreviewContent(empty: Boolean = false) {
    DashboardList(
        uiState = DashboardListUIState(
            textFieldState = TextFieldState(),
            items = buildList {
                repeat(25.takeIf { !empty } ?: 0) {
                    val p = Password(
                        passwordId = it.toLong(),
                        username = "User $it",
                        website = "Website",
                        score = Score.Weak,
                        vaultItemId = it.toLong(),
                        name = "${if (it >= 5) 'A' else 'B'} Item $it",
                        encryptedData = CryptographicData.EMPTY,
                        note = "This is a note for item $it",
                        totpSecret = null
                    )
                    add(p)
                }
            }.toPersistentList(),
            selectedItemIds = persistentSetOf(1, 2, 3),
            openedItemId = 0
        ),
        onEvent = {}
    )
}

@Preview(device = "spec:width=1080px,height=2340px,dpi=320")
@Composable
private fun DashboardContentPreview() {
    MaterialTheme {
        PreviewContent()
    }
}

@Preview(device = "spec:width=1080px,height=2340px,dpi=320")
@Composable
private fun DashboardContentEmptyPreview() {
    MaterialTheme {
        PreviewContent(empty = true)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview(device = "spec:width=673dp,height=841dp,orientation=landscape")
@Composable
private fun Test() {
    MaterialTheme {
        NavigableListDetailPaneScaffold(
            navigator = rememberListDetailPaneScaffoldNavigator<Unit>(),
            listPane = {
                PreviewContent()
            },
            detailPane = {
                Text(text = "TEXT")
            }
        )
    }
}