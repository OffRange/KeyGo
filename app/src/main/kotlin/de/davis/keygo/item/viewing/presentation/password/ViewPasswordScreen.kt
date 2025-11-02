package de.davis.keygo.item.viewing.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun ViewPasswordScreen(itemId: ItemId, navigate: (NavigationEvent) -> Unit) {
    val currentId by rememberUpdatedState(itemId)
    val viewModel: ViewPasswordViewModel = koinViewModel()
    LaunchedEffect(currentId) {
        viewModel.init(currentId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.navigationEvent) {
        navigate(it)
    }

    ViewPasswordContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}