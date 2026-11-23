package de.davis.keygo.feature.account.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.account.presentation.changepassword.ChangePasswordScreen
import de.davis.keygo.feature.account.presentation.setup.ConnectServerScreen
import kotlinx.serialization.Serializable

@Serializable
object AccountRoute : NavKey

@Serializable
object ChangePasswordRoute : NavKey

@Serializable
object ConnectServerRoute : NavKey

@Serializable
object DevicesRoute : NavKey

fun EntryProviderScope<NavKey>.accountEntries(
    metadata: Map<String, Any> = emptyMap(),
    onOpenChangePassword: () -> Unit,
    onOpenConnectServer: () -> Unit,
    onOpenDevices: () -> Unit,
    navigateUp: () -> Unit
) {
    entry<AccountRoute>(metadata = metadata) {
        AccountScreen(
            navigateUp = navigateUp,
            onOpenChangePassword = onOpenChangePassword,
            onOpenConnectServer = onOpenConnectServer,
            onOpenDevices = onOpenDevices,
        )
    }

    entry<ChangePasswordRoute>(metadata = metadata) {
        ChangePasswordScreen(onUp = navigateUp)
    }

    entry<ConnectServerRoute>(metadata = metadata) {
        ConnectServerScreen(navigateUp = navigateUp)
    }

    entry<DevicesRoute>(metadata = metadata) {

    }
}
