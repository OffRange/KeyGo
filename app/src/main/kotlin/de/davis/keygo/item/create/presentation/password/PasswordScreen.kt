package de.davis.keygo.item.create.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.core.presentation.ObserveAsEvents
import de.davis.keygo.core.presentation.model.NavigationEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordScreen(
    detailPaneInformation: DetailPaneInformation,
    navigate: (NavigationEvent) -> Unit
) {
    val viewmodel: PasswordViewModel = koinViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

    LaunchedEffect(detailPaneInformation) {
        viewmodel.init(detailPaneInformation)
    }

    ObserveAsEvents(viewmodel.navigationEvent) {
        navigate(it)
    }

    PasswordContent(
        state = state,
        onEvent = viewmodel::onEvent,
    )
}