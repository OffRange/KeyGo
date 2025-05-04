package de.davis.keygo.dashboard.presentation.model

sealed interface DashboardUIEvent {

    data object OnSearchSubmit : DashboardUIEvent
    data object OnSearchClear : DashboardUIEvent
    data object OnSearchCollapse : DashboardUIEvent

    data class OnClick(val vaultId: Long) : DashboardUIEvent
    data class OnLongClick(val vaultId: Long) : DashboardUIEvent
    data class OnDeleteRequest(val vaultId: Long) : DashboardUIEvent
}