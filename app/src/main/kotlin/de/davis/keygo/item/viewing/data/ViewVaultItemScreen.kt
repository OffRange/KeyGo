package de.davis.keygo.item.viewing.data

import androidx.compose.runtime.Composable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.item.viewing.presentation.password.ViewPasswordScreen

@Composable
fun ViewVaultItemScreen(itemId: ItemId, navigate: (NavigationEvent) -> Unit) {
    // TODO: figure out what type the id is
    ViewPasswordScreen(itemId = itemId, navigate = navigate)
}