package de.davis.keygo.feature.password_health.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object PasswordHealthRoute : NavKey

fun EntryProviderScope<NavKey>.passwordHealthEntries(
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<PasswordHealthRoute>(metadata = metadata) {
        PasswordHealthScreen()
    }
}
