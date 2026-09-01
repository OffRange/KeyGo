package de.davis.keygo.feature.item.create.presentation.totp

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.login.LoginScreen
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AssignTotpRoute(
    val totpUri: String,
    val itemId: String? = null,
) : NavKey {
    val selectedItemId: ItemId?
        get() = itemId?.let(UUID::fromString)
}

fun EntryProviderScope<NavKey>.assignTotpEntries(
    metadata: Map<String, Any> = emptyMap(),
    onImportFinished: () -> Unit,
    navigateUp: () -> Unit,
) {
    entry<AssignTotpRoute>(metadata = metadata) { route ->
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
