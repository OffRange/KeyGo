package de.davis.keygo.feature.password_health.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.presentation.model.FixFlow
import de.davis.keygo.feature.password_health.presentation.model.HealthSection
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent

internal fun LazyListScope.needsAttentionSection(
    sections: List<HealthSection>,
    pendingFix: FixFlow.Pending?,
    onEvent: (PasswordHealthUiEvent) -> Unit,
) {
    item(key = "needs-attention-title", contentType = ContentType.Title) {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.needs_attention_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    sections.forEach { section ->
        item(
            key = "severity-${section.severity}",
            contentType = ContentType.SeverityHeader
        ) {
            Text(
                text = stringResource(section.severity.labelRes),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .animateItem()
                    .padding(top = 12.dp, bottom = 4.dp),
            )
        }

        section.groups.forEach { group ->
            item(key = "group-${group.id}", contentType = ContentType.GroupLabel) {
                GroupLabel(group = group, modifier = Modifier.animateItem())
            }

            segmentedRows(group.orderedMembers, pendingFix, onEvent)
        }

        segmentedRows(section.standalone, pendingFix, onEvent)
    }
}

private fun LazyListScope.segmentedRows(
    items: List<ItemHealth>,
    pendingFix: FixFlow.Pending?,
    onEvent: (PasswordHealthUiEvent) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = { _, item -> item.itemId },
        contentType = { _, _ -> ContentType.Row },
    ) { index, item ->
        ItemHealthRow(
            itemHealth = item,
            shapes = segmentedShapesFor(index = index, count = items.size),
            pendingFix = pendingFix?.takeIf { it.itemId == item.itemId },
            onEvent = onEvent,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun GroupLabel(
    group: RelatedGroup,
    modifier: Modifier = Modifier,
) {
    val count = group.members.size
    val label = when (group.dominantRelation) {
        RelationType.Reused -> R.plurals.needs_attention_group_reused
        RelationType.Similar -> R.plurals.needs_attention_group_similar
    }

    Text(
        text = pluralStringResource(label, count, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun segmentedShapesFor(index: Int, count: Int): ListItemShapes =
    if (count == 1) ListItemDefaults.shapes(MaterialTheme.shapes.large)
    else ListItemDefaults.segmentedShapes(index, count)

private val FindingSeverity.labelRes: Int
    @StringRes
    get() = when (this) {
        FindingSeverity.Critical -> R.string.needs_attention_severity_critical
        FindingSeverity.High -> R.string.needs_attention_severity_high
        FindingSeverity.Medium -> R.string.needs_attention_severity_medium
    }

private enum class ContentType {
    Title,
    SeverityHeader,
    GroupLabel,
    Row,
}

@Preview
@Composable
private fun NeedsAttentionSectionPreview() {
    val reused = List(3) { index ->
        ItemHealth(
            itemId = newItemId(),
            title = listOf("Reddit", "Twitch", "Steam")[index],
            username = "user@example.com",
            issues = emptyList(),
        )
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                needsAttentionSection(
                    pendingFix = null,
                    onEvent = {},
                    sections = listOf(
                        HealthSection(
                            severity = FindingSeverity.Critical,
                            groups = emptyList(),
                            standalone = listOf(
                                ItemHealth(
                                    itemId = newItemId(),
                                    title = "GitHub",
                                    username = "user@example.com",
                                    issues = listOf(ItemIssue.Breached(occurrences = 4)),
                                ),
                            ),
                        ),
                        HealthSection(
                            severity = FindingSeverity.High,
                            groups = listOf(
                                RelatedGroup(
                                    id = reused.first().itemId,
                                    members = reused.toSet(),
                                    relations = setOf(
                                        HealthFinding.Relation(
                                            relatedItemIds = reused.mapTo(mutableSetOf()) { it.itemId },
                                            type = RelationType.Reused,
                                        ),
                                    ),
                                ),
                            ),
                            standalone = emptyList(),
                        ),
                        HealthSection(
                            severity = FindingSeverity.Medium,
                            groups = emptyList(),
                            standalone = listOf(
                                ItemHealth(
                                    itemId = newItemId(),
                                    title = "Old forum account",
                                    username = null,
                                    issues = listOf(ItemIssue.Weak(score = PasswordScore.Weak)),
                                ),
                                ItemHealth(
                                    itemId = newItemId(),
                                    title = "Router admin",
                                    username = "admin",
                                    issues = listOf(
                                        ItemIssue.Weak(score = PasswordScore.Ridiculous),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
