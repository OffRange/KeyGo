package de.davis.keygo.item.create.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.generated.item.VaultItemType
import de.davis.keygo.item.create.presentation.password.PasswordScreen

@Composable
fun EditVaultItemScreen(
    editItem: DetailItem.Edit,
    navigate: (NavigationEvent) -> Unit
) {
    when (editItem.type) {
        VaultItemType.Password -> {
            PasswordScreen(
                getBy = editItem.getBy,
                navigate = navigate
            )
        }
    }
}