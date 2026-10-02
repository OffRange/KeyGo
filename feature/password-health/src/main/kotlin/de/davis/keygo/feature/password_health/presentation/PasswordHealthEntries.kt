package de.davis.keygo.feature.password_health.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.serialization.Serializable

@Serializable
object PasswordHealthRoute : NavKey

const val ACTION_OPEN_PASSWORD_HEALTH = "de.davis.keygo.action.OPEN_PASSWORD_HEALTH"

fun EntryProviderScope<NavKey>.passwordHealthEntries(
    metadata: Map<String, Any> = emptyMap(),
    openItemId: ItemId?,
    openItem: (ItemId) -> Unit,
) {
    entry<PasswordHealthRoute>(metadata = metadata) {
        PasswordHealthScreen(openItemId = openItemId, openItem = openItem)
    }
}
