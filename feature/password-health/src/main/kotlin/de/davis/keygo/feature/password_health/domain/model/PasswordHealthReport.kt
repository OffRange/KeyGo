package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.util.domain.comparator.NaturalOrderComparator

data class ItemHealth(
    val itemId: ItemId,
    val title: String,
    val username: String?,
    val issues: List<ItemIssue>,
    val urls: List<String> = emptyList(),
) {
    val maxSeverity: FindingSeverity? = issues.maxOfOrNull { it.severity }

    val breach: ItemIssue.Breached? = issues.filterIsInstance<ItemIssue.Breached>().firstOrNull()
    val weak: ItemIssue.Weak? = issues.filterIsInstance<ItemIssue.Weak>().firstOrNull()
}

data class RelatedGroup(
    val id: ItemId,
    val members: Set<ItemHealth>,
    val relations: Set<HealthFinding.Relation>,
) {
    val maxSeverity =
        (members.mapNotNull { it.maxSeverity } + relations.map { it.type.severity }).max()

    /**
     * What the group is mostly about, which is what its label says.
     *
     * A group is one connected component over every relation edge, so a couple of exact
     * copies can sit inside a much wider similarity chain.
     */
    val dominantRelation: RelationType = relations
        .groupBy { it.type }
        .entries
        .maxWith(
            compareBy<Map.Entry<RelationType, List<HealthFinding.Relation>>> { (_, sharing) ->
                sharing.flatMapTo(mutableSetOf()) { it.relatedItemIds }.size
            }.thenBy { (type, _) -> type.severity },
        )
        .key
    val orderedMembers: List<ItemHealth> = members.sortedWith(
        compareByDescending<ItemHealth> { it.maxSeverity }
            .thenBy(NaturalOrderComparator) { it.title },
    )
}

data class PasswordHealthReport(
    val groups: List<RelatedGroup>,
    val standalone: List<ItemHealth>,
    val totalPasswordsScanned: Int,
    val gaps: Map<CheckKind, CheckGap> = emptyMap(),
    val unreadable: Set<ItemId> = emptySet(),
) {
    val needsAttentionCount: Int = groups.sumOf { it.members.size } + standalone.size
}
