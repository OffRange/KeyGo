package de.davis.keygo.dashboard.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.domain.`typealias`.ItemId
import de.davis.keygo.core.domain.`typealias`.ItemIdNone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class DashboardUIState(
    val textFieldState: TextFieldState,
    val items: ImmutableList<VaultItem> = persistentListOf(),
    val searchResult: ImmutableList<VaultSearchResult> = persistentListOf(),
    val selectedItemIds: ImmutableSet<ItemId> = persistentSetOf(),
    val openedItemId: ItemId = ItemIdNone,
)
