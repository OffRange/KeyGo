package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class CheckGap(
    val error: CheckError,
    val unchecked: Set<ItemId>,
)
