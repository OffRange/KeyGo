package de.davis.keygo.core.presentation.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.generated.item.VaultItemEnum

sealed interface NavigationEvent {

    data object NavigateBack : NavigationEvent
    data class NavigateToEdit(val vaultType: VaultItemEnum, val itemId: ItemId) : NavigationEvent
}