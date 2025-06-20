package de.davis.keygo.dashboard.presentation.model

import de.davis.keygo.generated.item.VaultItemEnum

sealed interface DashboardEvent {
    data class CreateNewItemRequest(val itemType: VaultItemEnum) : DashboardEvent
}