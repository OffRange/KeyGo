package de.davis.keygo.feature.account.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.account.presentation.changepassword.ChangePasswordScreen
import kotlinx.serialization.Serializable

@Serializable
object AccountRoute : NavKey

@Serializable
object ChangePasswordRoute : NavKey

fun EntryProviderScope<NavKey>.accountEntries(
    metadata: Map<String, Any> = emptyMap(),
    onOpenChangePassword: () -> Unit,
    navigateUp: () -> Unit
) {
    entry<AccountRoute>(metadata = metadata) {
        AccountScreen(navigateUp = navigateUp, onOpenChangePassword = onOpenChangePassword)
    }

    entry<ChangePasswordRoute>(metadata = metadata) {
        ChangePasswordScreen(onUp = navigateUp)
    }
}
