package de.davis.keygo.core.item.domain.alias

import java.util.UUID

typealias ItemId = UUID

fun newItemId(): ItemId = UUID.randomUUID()
