package de.davis.keygo.item.viewing.data

import androidx.compose.runtime.Composable
import de.davis.keygo.core.domain.model.navigation.DetailItem
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.item.viewing.presentation.password.ViewPasswordScreen

@Composable
fun ViewVaultItemScreen(viewItem: DetailItem.View, navigate: (NavigationEvent) -> Unit) {
    // TODO: figure out what type the id is
    ViewPasswordScreen(itemId = viewItem.itemId, navigate = navigate)
}