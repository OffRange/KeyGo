package de.davis.keygo.core.data.navigation

import androidx.navigation.NavOptionsBuilder
import de.davis.keygo.core.domain.annotation.OneTimeUsage
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.domain.model.navigation.NavigationAction
import de.davis.keygo.core.domain.navigation.Navigator
import de.davis.keygo.core.presentation.model.RouteDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.Single

@Single
class NavigatorImpl : Navigator {

    private val _navigationAction = Channel<NavigationAction>()

    @OneTimeUsage
    override val navigationAction = _navigationAction.receiveAsFlow()

    override suspend fun navigateToDetail(destination: DetailItem) {
        _navigationAction.send(
            NavigationAction.NavigateToDetail(destination = destination)
        )
    }

    override suspend fun navigateTo(
        destination: RouteDestination,
        builder: NavOptionsBuilder.() -> Unit
    ) {
        _navigationAction.send(
            NavigationAction.NavigateTo(
                destination = destination,
                builder = builder
            )
        )
    }

    override suspend fun navigateUp(detail: Boolean) {
        _navigationAction.send(NavigationAction.NavigateUp(detail))
    }
}