package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

sealed interface HealthFinding {
    data class Item(
        val id: ItemId,
        val issue: ItemIssue,
    ) : HealthFinding

    data class Relation(
        val relatedItemIds: Set<ItemId>,
        val type: RelationType,
    ) : HealthFinding
}
