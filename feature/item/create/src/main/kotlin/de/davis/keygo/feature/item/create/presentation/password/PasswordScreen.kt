package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordScreen(
    detailPaneInformation: DetailPaneInformation = DetailPaneInformation.Init.New(
        itemType = VaultItemType.Password
    ),
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