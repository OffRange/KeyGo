package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class CheckGap(
    val reason: GapReason,
    val unchecked: Set<ItemId>,
)
