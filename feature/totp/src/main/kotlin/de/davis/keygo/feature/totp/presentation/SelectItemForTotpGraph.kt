package de.davis.keygo.feature.totp.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.ui.RouteDestination
import kotlinx.serialization.Serializable

@Serializable
data class SelectItemForTotpRoute(val totpUri: String) : RouteDestination

fun NavGraphBuilder.selectItemForTotpGraph(
    onItemSelected: (totpUri: String, itemId: ItemId) -> Unit,
    onCreateNew: (totpUri: String) -> Unit,
) {
    composable<SelectItemForTotpRoute> { entry ->
        val route = entry.toRoute<SelectItemForTotpRoute>()
        SelectItemForTotpScreen(
            totpUri = route.totpUri,
            onItemSelected = { itemId -> onItemSelected(route.totpUri, itemId) },
            onCreateNew = { onCreateNew(route.totpUri) },
        )
    }
}
