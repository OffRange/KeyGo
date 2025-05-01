package de.davis.keygo.dashboard.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.domain.model.VaultItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

data class DashboardUIState(
    val textFieldState: TextFieldState,
    val items: ImmutableList<VaultItem> = persistentListOf(),
    val selectedItemIds: ImmutableSet<Long> = persistentSetOf(),
    val openedItemId: Long = -1,
    val navEvent: DashboardNavEvent = DashboardNavEvent.None,
)
