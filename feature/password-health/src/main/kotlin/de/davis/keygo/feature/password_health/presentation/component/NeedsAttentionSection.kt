package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.presentation.model.AttentionEntry
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.PasswordIssueType
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

internal fun LazyListScope.needsAttentionSection(
    state: PasswordHealthUiState,
) {
    item(key = "NeedsAttentionSection-header") {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.needs_attention_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    itemsIndexed(
        items = state.attentionEntries,
        key = { _, entry -> entry.id }
    ) { index, entry ->
        AttentionRow(
            entry = entry,
            shapes = ListItemDefaults.segmentedShapes(index, state.attentionEntries.size),
            modifier = Modifier.animateItem()
        )
    }
}

@Composable
private fun AttentionRow(
    entry: AttentionEntry,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        overlineContent = if (entry.issueType is PasswordIssueType.Breached) {
            {
                Text(
                    text = pluralStringResource(
                        R.plurals.needs_attention_found_in_breach,
                        entry.issueType.count,
                        entry.issueType.count,
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,
        supportingContent = entry.username?.let { username ->
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview
@Composable
private fun NeedsAttentionSectionPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                needsAttentionSection(
                    state = PasswordHealthUiState(
                        totalPasswordCount = 10,
                        attentionEntries = listOf(
                            AttentionEntry(
                                id = newItemId(),
                                title = "Example 1",
                                username = null,
                                issueType = PasswordIssueType.Weak
                            ),
                            AttentionEntry(
                                id = newItemId(),
                                title = "Example 2",
                                username = "User",
                                issueType = PasswordIssueType.Reused
                            ),
                            AttentionEntry(
                                id = newItemId(),
                                title = "Example 3",
                                username = null,
                                issueType = PasswordIssueType.Breached(count = 5)
                            ),
                        )
                    )
                )
            }
        }
    }
}
