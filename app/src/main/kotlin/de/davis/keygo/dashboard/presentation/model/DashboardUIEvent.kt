package de.davis.keygo.dashboard.presentation.model

sealed interface DashboardUIEvent {

    data object OnSearchSubmitted : DashboardUIEvent
    data class OnClicked(val vaultId: Long) : DashboardUIEvent
    data class OnLongClicked(val vaultId: Long) : DashboardUIEvent
    data class OnDeleteRequested(val vaultId: Long) : DashboardUIEvent
}