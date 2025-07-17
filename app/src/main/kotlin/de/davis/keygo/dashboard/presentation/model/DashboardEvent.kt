package de.davis.keygo.dashboard.presentation.model

import de.davis.keygo.generated.item.VaultItemType

sealed interface DashboardEvent {
    data class CreateNewItemRequest(val itemType: VaultItemType) : DashboardEvent
    data class CreateOrUpdate(val totpUri: String) : DashboardEvent
}