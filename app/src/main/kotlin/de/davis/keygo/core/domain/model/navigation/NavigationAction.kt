package de.davis.keygo.core.domain.model.navigation

import androidx.navigation.NavOptionsBuilder
import de.davis.keygo.core.presentation.model.RouteDestination

sealed interface NavigationAction {
    data class NavigateToDetail(
        val destination: DetailItem,
    ) : NavigationAction

    data class NavigateTo(
        val destination: RouteDestination,
        val builder: NavOptionsBuilder.() -> Unit = {}
    ) : NavigationAction

    data class NavigateUp(val detail: Boolean = false) : NavigationAction
}
