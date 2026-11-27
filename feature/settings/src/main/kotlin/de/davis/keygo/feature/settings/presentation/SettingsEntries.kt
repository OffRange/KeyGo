package de.davis.keygo.feature.settings.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute : NavKey

fun EntryProviderScope<NavKey>.settingsEntries(
    metadata: Map<String, Any> = emptyMap(),
    onShowLibraries: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    entry<SettingsRoute>(metadata = metadata) {
        SettingsScreen(
            showLibraries = onShowLibraries,
            onOpenBackup = onOpenBackup,
            onOpenAccount = onOpenAccount,
        )
    }
}
