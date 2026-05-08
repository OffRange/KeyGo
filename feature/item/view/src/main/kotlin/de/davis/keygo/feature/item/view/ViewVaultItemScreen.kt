package de.davis.keygo.feature.item.view

import androidx.compose.runtime.Composable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.view.login.ViewLoginScreen

@Composable
fun ViewVaultItemScreen(itemId: ItemId, navigate: (NavigationEvent) -> Unit) {
    // TODO: figure out what type the id is
    ViewLoginScreen(itemId = itemId, navigate = navigate)
}
