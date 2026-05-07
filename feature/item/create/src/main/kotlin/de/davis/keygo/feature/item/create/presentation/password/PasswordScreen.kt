package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import org.koin.androidx.compose.koinViewModel

@Composable
fun PasswordScreen(
    detailPaneInformation: DetailPaneInformation = DetailPaneInformation.Init.New(
        itemType = VaultItemType.Login
    ),
    passwordCreated: (ItemId) -> Unit,
    navigateBack: () -> Unit
) {
    val viewmodel: PasswordViewModel = koinViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

    LaunchedEffect(detailPaneInformation) {
        viewmodel.init(detailPaneInformation)
    }

    ObserveAsEvents(viewmodel.itemCreatedEvent) {
        when (it) {
            null -> navigateBack()
            else -> passwordCreated(it)
        }
    }

    PasswordContent(
        state = state,
        onEvent = viewmodel::onEvent,
    )
}