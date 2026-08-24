package de.davis.keygo.feature.item.create.presentation.totp

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.login.LoginScreen
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The login form for a chosen item, or for a new one when [itemId] is null.
 *
 * The id travels as a String because [ItemId] is a [UUID] and type-safe navigation has no
 * [androidx.navigation.NavType] for it. Supplying one through a typeMap for a single nullable id
 * costs more than the conversion does.
 */
@Serializable
data class AssignTotpRoute(
    val totpUri: String,
    val itemId: String? = null,
) : RouteDestination {
    val selectedItemId: ItemId?
        get() = itemId?.let(UUID::fromString)
}

/**
 * The last step of a deep-linked import: the login form, opened on the item the picker chose, with
 * the scanned code already pending on it.
 *
 * The picker itself lives in the totp feature, which knows nothing about a login form. It hands the
 * uri back to whoever wired the two graphs together, and that caller navigates to
 * [AssignTotpRoute].
 *
 * @param onImportFinished the import is over and the code has been saved. Leads back into the app
 * with the import routes popped, so back does not return the user to a code they already handled.
 */
fun NavGraphBuilder.assignTotpGraph(
    onImportFinished: () -> Unit,
    navigateUp: () -> Unit,
) {
    composable<AssignTotpRoute> { entry ->
        val route = entry.toRoute<AssignTotpRoute>()
        LoginScreen(
            detailPaneInformation = route.selectedItemId?.let { itemId ->
                DetailPaneInformation.Init.Existing(
                    itemType = VaultItemType.Login,
                    id = itemId,
                    pendingTotpUri = route.totpUri,
                )
            } ?: DetailPaneInformation.Init.New(
                itemType = VaultItemType.Login,
                pendingTotpUri = route.totpUri,
            ),
            loginCreated = { onImportFinished() },
            navigateBack = navigateUp,
        )
    }
}
