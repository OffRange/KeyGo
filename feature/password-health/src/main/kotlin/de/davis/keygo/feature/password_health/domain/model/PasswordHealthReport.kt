package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class ItemHealth(
    val itemId: ItemId,
    val title: String,
    val username: String?,
    val issues: List<ItemIssue>
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

    val dominantRelation: RelationType = relations.maxBy { it.type.severity }.type
    val orderedMembers: List<ItemHealth> = members.sortedByDescending { it.maxSeverity }
}

data class PasswordHealthReport(
    val groups: List<RelatedGroup>,
    val standalone: List<ItemHealth>,
    val totalPasswordsScanned: Int,
)
