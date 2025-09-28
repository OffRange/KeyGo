package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

// TODO: extract additional schemes (like https) from "value" and store them separately
data class DomainInfo(
    val passwordId: ItemId = 0,
    val value: String,
    val eTLD1: String?,
)