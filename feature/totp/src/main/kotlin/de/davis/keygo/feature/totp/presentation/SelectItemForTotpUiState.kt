package de.davis.keygo.feature.totp.presentation

import de.davis.keygo.core.item.domain.alias.ItemId

internal data class SelectItemForTotpUiState(
    val suggestedItemIds: Set<ItemId> = emptySet(),
)
