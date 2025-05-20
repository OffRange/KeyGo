package de.davis.keygo.item.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.presentation.model.RouteDestination
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordScreen(navigate: (RouteDestination) -> Unit) {
    val viewmodel: PasswordViewModel = koinViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

    PasswordContent(
        state = state,
        onEvent = viewmodel::onEvent,
    )
}