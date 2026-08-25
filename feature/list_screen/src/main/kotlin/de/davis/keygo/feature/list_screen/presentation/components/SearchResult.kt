package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.presentation.model.SearchMatchField
import de.davis.keygo.feature.list_screen.presentation.model.SearchState
import de.davis.keygo.feature.list_screen.presentation.model.matchedFields

@Composable
internal fun SearchResult(
    searchState: SearchState,
    onResultClick: (ItemId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedContent(targetState = searchState.results.isEmpty()) { isEmpty ->
            when (isEmpty) {
                true -> EmptySearchResult(query = searchState.query)

                false -> SearchResultContent(
                    searchState = searchState,
                    onResultClick = onResultClick,
                )
            }
        }
    }
}

@Composable
private fun SearchResultContent(
    searchState: SearchState,
    onResultClick: (ItemId) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (searchState.query.isNotBlank())
            item(key = ResultCountKey, contentType = ResultCountKey) {
                Text(
                    text = pluralStringResource(
                        R.plurals.search_result_count,
                        searchState.results.size,
                        searchState.results.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                )
            }

        itemsIndexed(
            searchState.results,
            key = { _, item -> item.id },
            contentType = { _, item -> item.itemType },
        ) { index, result ->
            SearchResultRow(
                index = index,
                count = searchState.results.size,
                result = result,
                query = searchState.query,
                onClick = { onResultClick(result.id) },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    index: Int,
    count: Int,
    result: LiteItemSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    val (typeLabel, typeIcon) = result.itemType.presentation
    val matchedFields = result.matchedFields()

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        supportingContent = {
            if (query.isBlank() || matchedFields.isEmpty()) Text(text = typeLabel)
            else MatchedFields(fields = matchedFields)
        },
        leadingContent = { ItemTypeBadge(icon = typeIcon, contentDescription = typeLabel) },
    ) {
        Text(
            text = highlightMatch(result.name, query),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MatchedFields(fields: List<SearchMatchField>) {
    val labels = fields.map { it.label }
    val reason = stringResource(R.string.search_match_reason, labels.joinToString())

    FlowRow(
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = reason },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fields.forEachIndexed { index, field ->
            MatchedFieldChip(icon = field.icon, label = labels[index])
        }
    }
}

@Composable
private fun MatchedFieldChip(icon: ImageVector, label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ItemTypeBadge(icon: ImageVector, contentDescription: String) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun highlightMatch(name: String, query: String): AnnotatedString {
    val style = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    return remember(name, query, style) {
        buildAnnotatedString {
            append(name)
            if (query.isBlank()) return@buildAnnotatedString

            var start = name.indexOf(query, ignoreCase = true)
            while (start >= 0) {
                val end = start + query.length
                addStyle(style, start, end)
                start = name.indexOf(query, startIndex = end, ignoreCase = true)
            }
        }
    }
}

@Composable
private fun EmptySearchResult(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.search_no_matches),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (query.isBlank()) stringResource(R.string.search_nothing_to_search)
            else stringResource(R.string.search_no_matches_for, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val ResultCountKey = "search-result-count"

@Preview
@Composable
private fun SearchResultPreview() {
    val results = remember {
        listOf(
            searchResult("GitHub", matchedName = true),
            searchResult("GitLab", matchedName = true, matchedTag = true),
            searchResult("Digital Ocean", matchedUsername = true),
            searchResult("Recovery codes", matchedNote = true),
            searchResult(
                "Travel card",
                itemType = VaultItemType.CreditCard,
                matchedNote = true,
                matchedTag = true,
            ),
        )
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchResult(
                searchState = SearchState(query = "git", results = results),
                onResultClick = {},
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
                searchState = SearchState(query = "banana", results = emptyList()),
                onResultClick = {},
            )
        }
    }
}

private fun searchResult(
    name: String,
    itemType: VaultItemType = VaultItemType.Login,
    matchedName: Boolean = false,
    matchedUsername: Boolean = false,
    matchedNote: Boolean = false,
    matchedTag: Boolean = false,
) = LiteItemSearchResult(
    id = newItemId(),
    name = name,
    itemType = itemType,
    pinned = false,
    matchedName = matchedName,
    matchedUsername = matchedUsername,
    matchedNote = matchedNote,
    matchedTag = matchedTag,
)
