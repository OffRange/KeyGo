package de.davis.keygo.feature.totp.presentation

import de.davis.keygo.core.item.domain.alias.ItemId

/**
 * What the picker knows about the scanned code.
 *
 * @param suggestedItemIds the logins on the code's own registrable domain, shown first. An empty set
 * is the ordinary case for a code whose domain matches nothing, and is also what an unreadable code
 * produces, since the redirect that starts the import already turned those away.
 */
internal data class SelectItemForTotpUiState(
    val suggestedItemIds: Set<ItemId> = emptySet(),
)
