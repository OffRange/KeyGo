package de.davis.keygo.viewing.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.domain.`typealias`.ItemId
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ViewPasswordScreen(itemId: ItemId) {
    val currentId by rememberUpdatedState(itemId)
    val viewModel: ViewPasswordViewModel = koinViewModel { parametersOf(currentId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ViewPasswordContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}