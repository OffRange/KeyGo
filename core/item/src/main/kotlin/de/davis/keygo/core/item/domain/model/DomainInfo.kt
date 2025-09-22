package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class DomainInfo(
    val passwordId: ItemId,
    val value: String,
    val eTLD1: String?,
)