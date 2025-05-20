package de.davis.keygo.core.domain.navigation

import androidx.navigation.NavOptionsBuilder
import de.davis.keygo.core.domain.annotation.OneTimeUsage
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.domain.model.navigation.NavigationAction
import de.davis.keygo.core.presentation.model.RouteDestination
import kotlinx.coroutines.flow.Flow

interface Navigator {

    @OneTimeUsage
    val navigationAction: Flow<NavigationAction>

    suspend fun navigateToDetail(destination: DetailItem)

    suspend fun navigateTo(
        destination: RouteDestination,
        builder: NavOptionsBuilder.() -> Unit = {}
    )

    suspend fun navigateUp(detail: Boolean = false)
}