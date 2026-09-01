package de.davis.keygo.feature.totp.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.serialization.Serializable

@Serializable
data class SelectItemForTotpRoute(val totpUri: String) : NavKey

fun EntryProviderScope<NavKey>.selectItemForTotpEntries(
    metadata: Map<String, Any> = emptyMap(),
    onItemSelected: (totpUri: String, itemId: ItemId) -> Unit,
    onCreateNew: (totpUri: String) -> Unit,
) {
    entry<SelectItemForTotpRoute>(metadata = metadata) { route ->
        SelectItemForTotpScreen(
            totpUri = route.totpUri,
            onItemSelected = { itemId -> onItemSelected(route.totpUri, itemId) },
            onCreateNew = { onCreateNew(route.totpUri) },
        )
    }
}
