package de.davis.keygo.dashboard.presentation.model

sealed interface DashboardNavEvent {
    data object None : DashboardNavEvent
}