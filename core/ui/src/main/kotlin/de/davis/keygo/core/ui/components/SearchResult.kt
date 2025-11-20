package de.davis.keygo.core.ui.components

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
import de.davis.keygo.core.ui.R

@Composable
internal fun <I> SearchResult(
    searchResult: List<I>,
    idOf: (I) -> Any,
    nameOf: (I) -> String,
    matchedInName: (I) -> Boolean,
    matchedInNote: (I) -> Boolean,
    onClick: (I) -> Unit,
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
                        idOf = idOf,
                        nameOf = nameOf,
                        matchedInName = matchedInName,
                        matchedInNote = matchedInNote,
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
private fun <I> SearchResultContent(
    searchResult: List<I>,
    idOf: (I) -> Any,
    nameOf: (I) -> String,
    matchedInName: (I) -> Boolean,
    matchedInNote: (I) -> Boolean,
    onClick: (I) -> Unit,
    cardColors: CardColors
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
    ) {
        items(searchResult, key = { idOf(it) }) { item ->
            VaultItem(
                headlineContent = {
                    Text(text = nameOf(item))
                },
                supportingContent = {
                    when {
                        matchedInName(item) && matchedInNote(item) -> {
                            Text(text = stringResource(R.string.match_name_and_note))
                        }

                        matchedInName(item) -> {
                            Text(text = stringResource(R.string.match_name))
                        }

                        matchedInNote(item) -> {
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
    val items = listOf("Example Item 1", "Example Item 2")
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchResult(
                searchResult = items,
                idOf = { it },
                nameOf = { it },
                matchedInName = { true },
                matchedInNote = { it == items.first() },
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
                searchResult = listOf<String>(),
                idOf = { it },
                nameOf = { "" },
                matchedInName = { true },
                matchedInNote = { false },
                onClick = {}
            )
        }
    }
}