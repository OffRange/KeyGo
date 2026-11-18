package de.davis.keygo.feature.account.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.account.presentation.models.AccountEvent
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun AccountScreen(
    onOpenChangePassword: () -> Unit,
    onOpenConnectServer: () -> Unit,
    navigateUp: () -> Unit
) {
    val viewModel = koinViewModel<AccountViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(flow = viewModel.event) {
        when (it) {
            AccountEvent.NavigateToChangePassword -> onOpenChangePassword()
            AccountEvent.NavigateToConnectServer -> onOpenConnectServer()
            AccountEvent.NavigateBack -> navigateUp()
        }
    }

    AccountContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}