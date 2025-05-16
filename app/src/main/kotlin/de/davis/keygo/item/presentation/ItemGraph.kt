package de.davis.keygo.item.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import de.davis.keygo.generated.RouteDestination
import de.davis.keygo.generated.item.VaultItemEnum
import de.davis.keygo.item.presentation.dialog.SelectItemContent
import de.davis.keygo.item.presentation.password.PasswordScreen
import de.davis.keygo.processor.annotation.Route

@Route(name = "SelectItem", parent = "Main")
fun NavGraphBuilder.itemGraph(navigate: (RouteDestination) -> Unit) {
    dialog<RouteDestination.Main.SelectItem> {
        SelectItemContent(
            onSelect = {
                when (it) {
                    VaultItemEnum.Password -> {
                        navigate(RouteDestination.Main.PasswordScreen)
                    }
                }
            }
        )
    }

    composable<RouteDestination.Main.PasswordScreen> {
        PasswordScreen(navigate = navigate)
    }
}