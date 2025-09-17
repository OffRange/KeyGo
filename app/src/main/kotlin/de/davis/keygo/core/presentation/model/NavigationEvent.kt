package de.davis.keygo.core.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

sealed interface NavigationEvent {

    data object NavigateBack : NavigationEvent
    data class NavigateToEdit(val vaultType: VaultItemType, val itemId: ItemId) : NavigationEvent
}