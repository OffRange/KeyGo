package de.davis.keygo.feature.item.create.presentation.totp

import de.davis.keygo.core.item.domain.alias.ItemId

/**
 * What the picker knows about the scanned code.
 *
 * @param suggestedItemIds the logins on the code's own registrable domain, shown first. An empty
 * set is the ordinary case for a code whose domain matches nothing, not an error.
 * @param parseError the code could not be read at all, so there is nothing to attach anywhere.
 */
internal data class SelectItemForTotpUiState(
    val suggestedItemIds: Set<ItemId> = emptySet(),
    val parseError: Boolean = false,
)
