package de.davis.keygo.core.presentation.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.generated.item.VaultItemType

sealed interface NavigationEvent {

    data object NavigateBack : NavigationEvent
    data class NavigateToEdit(val vaultType: VaultItemType, val itemId: ItemId) : NavigationEvent
}