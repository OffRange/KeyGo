package de.davis.keygo.feature.totp.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.ui.RouteDestination
import kotlinx.serialization.Serializable

/**
 * Where a deep-linked code lands once the user is through the door. The uri travels whole here,
 * unlike on the route that carried it through the gate, because only the deep link's own
 * `otpauth://totp/{totpInfo}?{queries}` pattern forces that split.
 */
@Serializable
data class SelectItemForTotpRoute(val totpUri: String) : RouteDestination

/**
 * Asks which login a validated code belongs to, and lets the caller decide where the answer leads.
 *
 * The destination is the caller's to pick: the code ends up on a login form, and this module knows
 * nothing about one. Both callbacks hand the uri back out, because the form needs it and only this
 * graph's route is holding it.
 *
 * @param onItemSelected the user picked an existing login to attach the code to.
 * @param onCreateNew the user chose to attach the code to a login that does not exist yet.
 */
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
