package de.davis.keygo.feature.password_health.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordHealthScreen(openItemId: ItemId?, openItem: (ItemId) -> Unit) {
    val viewModel = koinViewModel<PasswordHealthViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(PasswordHealthUiEvent.RunHealthCheck)
    }

    ObserveAsEvents(flow = viewModel.events) {
        when (it) {
            is PasswordHealthEvent.OpenItem -> openItem(it.itemId)
        }
    }

    PasswordHealthContent(
        state = state,
        openItemId = openItemId.takeUnless { LocalIsInSinglePaneMode.current },
        onEvent = viewModel::onEvent,
    )
}
