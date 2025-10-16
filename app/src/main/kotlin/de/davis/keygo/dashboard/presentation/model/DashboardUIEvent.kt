package de.davis.keygo.dashboard.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.dashboard.domain.model.Filter

sealed interface DashboardUIEvent {

    data object OnSearchSubmit : DashboardUIEvent
    data object OnSearchClear : DashboardUIEvent
    data object OnSearchCollapse : DashboardUIEvent

    data object CloseItem : DashboardUIEvent
    data object OpenFirstItem : DashboardUIEvent
    data class OnCreateNewItemRequest(val itemType: VaultItemType) : DashboardUIEvent

    data class OnOpen(val vaultId: ItemId) : DashboardUIEvent
    data class OnOpenOrSelect(val vaultId: ItemId) : DashboardUIEvent
    data class OnLongClick(val vaultId: ItemId) : DashboardUIEvent
    data class OnDeleteRequest(val vaultId: ItemId) : DashboardUIEvent

    data class OnFilterChange(val filter: Filter) : DashboardUIEvent
}