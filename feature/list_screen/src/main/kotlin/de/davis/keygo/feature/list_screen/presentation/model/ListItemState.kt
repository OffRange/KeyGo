package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault

@Stable
data class ListItemState(
    val items: List<LiteItem> = emptyList(),
    val searchResults: List<LiteItem> = emptyList(),
    val hasSearchQuery: Boolean = false,
    val selectedItemIds: Set<ItemId> = emptySet(),
    val highlightedId: ItemId? = null,
    val vaults: List<VaultMetadata> = emptyList(),
    val selectedVault: SelectedVault = SelectedVault.All,
)
