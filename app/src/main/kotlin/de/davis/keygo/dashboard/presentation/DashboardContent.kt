package de.davis.keygo.dashboard.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.dashboard.presentation.component.KeyGoLazyColumn
import de.davis.keygo.dashboard.presentation.component.KeyGoLazyItem
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(uiState: DashboardUIState, onEvent: (DashboardUIEvent) -> Unit) {
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    val searchBarState = rememberSearchBarState()
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

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = uiState.textFieldState,
            searchBarState = searchBarState,
            onSearch = {
                scope.launch { searchBarState.animateToCollapsed() }
                onEvent(DashboardUIEvent.OnSearchSubmitted)
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                AnimatedContent(searchBarState.currentValue) {
                    when (it) {
                        SearchBarValue.Collapsed -> Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_content_description)
                        )

                        else -> IconButton(
                            onClick = {
                                scope.launch { searchBarState.animateToCollapsed() }
                                uiState.textFieldState.clearText()
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
                inputField = inputField,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.fillMaxWidth()
            )

            if (shouldBeDocked) {
                ExpandedDockedSearchBar(
                    state = searchBarState,
                    inputField = inputField,
                ) {

                }
            } else {
                ExpandedFullScreenSearchBar(
                    state = searchBarState,
                    inputField = inputField,
                ) {

                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .padding(it)
        ) {
            CompositionLocalProvider(
                LocalViewConfiguration provides newViewConfiguration,
            ) {
                KeyGoLazyColumn(
                    items = uiState.items,
                    selectedItemIds = uiState.selectedItemIds,
                    openedItemId = uiState.openedItemId,
                    itemContent = { item, containerColor, header ->
                        KeyGoLazyItem(
                            item = item,
                            header = header,
                            modifier = Modifier.combinedClickable(
                                onLongClick = {
                                    onEvent(DashboardUIEvent.OnLongClicked(item.vaultItemId))
                                },
                                onClick = {
                                    onEvent(DashboardUIEvent.OnClicked(item.vaultItemId))
                                },
                            ),
                            containerColor = containerColor,
                        )
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun DashboardContentPreview() {
    MaterialTheme {
        DashboardContent(
            uiState = DashboardUIState(
                textFieldState = TextFieldState(),
                items = buildList {
                    repeat(25) {
                        val p = Password(
                            it.toLong(),
                            "Item $it",
                            "Description $it",
                            it.toLong(),
                            "${if (it >= 5) 'a' else 'P'}assword $it",
                            CryptographicData.EMPTY,
                            "Password $it"
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
}