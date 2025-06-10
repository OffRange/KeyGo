package de.davis.keygo.dashboard.presentation.model

import de.davis.keygo.core.domain.`typealias`.ItemId
import de.davis.keygo.dashboard.domain.model.Filter

sealed interface DashboardUIEvent {

    data object OnSearchSubmit : DashboardUIEvent
    data object OnSearchClear : DashboardUIEvent
    data object OnSearchCollapse : DashboardUIEvent

    data class OnOpen(val vaultId: ItemId) : DashboardUIEvent
    data class OnOpenOrSelect(val vaultId: ItemId) : DashboardUIEvent
    data class OnLongClick(val vaultId: ItemId) : DashboardUIEvent
    data class OnDeleteRequest(val vaultId: ItemId) : DashboardUIEvent

    data class OnFilterChange(val filter: Filter) : DashboardUIEvent
}