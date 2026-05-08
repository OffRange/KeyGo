package de.davis.keygo.feature.item.view.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun ViewLoginScreen(itemId: ItemId, navigate: (NavigationEvent) -> Unit) {
    val currentId by rememberUpdatedState(itemId)
    val viewModel: ViewLoginViewModel = koinViewModel()
    LaunchedEffect(currentId) {
        viewModel.init(currentId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.navigationEvent) {
        navigate(it)
    }

    ViewLoginContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}
