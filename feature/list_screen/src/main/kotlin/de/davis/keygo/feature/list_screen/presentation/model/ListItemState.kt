package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.lite.LiteItem

@Stable
internal data class ListItemState(
    val items: List<LiteItem> = emptyList(),
    val searchResults: List<LiteItem> = emptyList(),
    val hasSearchQuery: Boolean = false,
    val selectedItemIds: Set<ItemId> = emptySet(),
    val highlightedId: ItemId? = null,
    val vaultState: VaultState = VaultState.Closed,
    val vaults: List<VaultMetadata> = emptyList(),
    val vaultContext: VaultContext = VaultContext.NoSpecific,
)
