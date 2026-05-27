package de.davis.keygo.feature.item.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.view.creditcard.ViewCreditCardScreen
import de.davis.keygo.feature.item.view.login.ViewLoginScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun ViewVaultItemScreen(itemId: ItemId, navigate: (NavigationEvent) -> Unit) {
    val currentId by rememberUpdatedState(itemId)
    val viewModel: ViewVaultItemViewModel = koinViewModel()
    LaunchedEffect(currentId) {
        viewModel.init(currentId)
    }

    val itemType by viewModel.itemType.collectAsStateWithLifecycle()

    when (itemType) {
        VaultItemType.Login -> ViewLoginScreen(itemId = itemId, navigate = navigate)
        VaultItemType.CreditCard -> ViewCreditCardScreen(itemId = itemId, navigate = navigate)
        null -> Unit
    }
}
