package de.davis.keygo.feature.settings.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.settings.presentation.changepassword.ChangePasswordScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute : NavKey

@Serializable
object ChangePasswordRoute : NavKey

/** Change password is settings continued, not a flow of its own, so both share [metadata]. */
fun EntryProviderScope<NavKey>.settingsEntries(
    metadata: Map<String, Any> = emptyMap(),
    onOpenChangePassword: () -> Unit,
    onShowLibraries: () -> Unit,
    onOpenBackup: () -> Unit,
    onUp: () -> Unit,
) {
    entry<SettingsRoute>(metadata = metadata) {
        SettingsScreen(
            showLibraries = onShowLibraries,
            onOpenChangePassword = onOpenChangePassword,
            onOpenBackup = onOpenBackup,
        )
    }

    entry<ChangePasswordRoute>(metadata = metadata) {
        ChangePasswordScreen(onUp = onUp)
    }
}
