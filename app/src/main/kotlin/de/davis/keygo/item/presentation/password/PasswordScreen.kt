package de.davis.keygo.item.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.alias.ItemIdNone
import de.davis.keygo.core.presentation.ObserveAsEvents
import de.davis.keygo.core.presentation.model.NavigationEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordScreen(itemId: ItemId = ItemIdNone, navigate: (NavigationEvent) -> Unit) {
    val viewmodel: PasswordViewModel = koinViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

    LaunchedEffect(itemId) {
        viewmodel.init(itemId)
    }

    ObserveAsEvents(viewmodel.navigationEvent) {
        navigate(it)
    }

    PasswordContent(
        state = state,
        onEvent = viewmodel::onEvent,
    )
}