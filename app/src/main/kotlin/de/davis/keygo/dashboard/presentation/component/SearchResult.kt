package de.davis.keygo.dashboard.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SearchResult(
    searchResult: ImmutableList<LiteVaultItemSearchResult>,
    onClick: (LiteVaultItemSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardDefaults.cardColors()
) {
    val isEmpty = remember(searchResult) { searchResult.isEmpty() }

    Box(modifier = modifier) {
        AnimatedContent(isEmpty) {
            when (it) {
                true -> {
                    EmptySearchResult()
                }

                false -> {
                    SearchResultContent(
                        onClick = onClick,
                        searchResult = searchResult,
                        cardColors = cardColors
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResult() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(R.string.match_not_found))
    }
}

@Composable
private fun SearchResultContent(
    searchResult: ImmutableList<LiteVaultItemSearchResult>,
    onClick: (LiteVaultItemSearchResult) -> Unit,
    cardColors: CardColors
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
    ) {
        items(searchResult, key = { it.vaultItemId }) { item ->
            VaultItem(
                headlineContent = {
                    Text(text = item.name)
                },
                supportingContent = {
                    when {
                        item.matchedName && item.matchedNote -> {
                            Text(text = stringResource(R.string.match_name_and_note))
                        }

                        item.matchedName -> {
                            Text(text = stringResource(R.string.match_name))
                        }

                        item.matchedNote -> {
                            Text(text = stringResource(R.string.match_note))
                        }
                    }
                },
                modifier = Modifier.clickable {
                    onClick(item)
                },
                cardColors = cardColors
            )
        }
    }
}

@Preview
@Composable
private fun SearchResultPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchResult(
                searchResult = persistentListOf(
                    LiteVaultItemSearchResult(
                        vaultItemId = 1,
                        name = "Test",
                        matchedName = true,
                        matchedNote = false
                    ),
                    LiteVaultItemSearchResult(
                        vaultItemId = 2,
                        name = "Test2",
                        matchedName = true,
                        matchedNote = true
                    ),
                ),
                onClick = {}
            )
        }
    }
}

@Preview
@Composable
private fun NoMatchPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchResult(
                searchResult = persistentListOf(),
                onClick = {}
            )
        }
    }
}