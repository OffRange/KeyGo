package de.davis.keygo.dashboard.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class DashboardListUIState(
    val textFieldState: TextFieldState,
    val items: ImmutableList<LiteItem> = persistentListOf(),
    val searchResult: ImmutableList<LiteVaultItemSearchResult> = persistentListOf(),
    val selectedItemIds: ImmutableSet<ItemId> = persistentSetOf(),
    val openedItemId: ItemId = ItemIdNone,
)
