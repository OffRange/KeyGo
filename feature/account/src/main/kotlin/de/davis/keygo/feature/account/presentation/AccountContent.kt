package de.davis.keygo.feature.account.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.settings.SettingsList
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.components.AccountBanner
import de.davis.keygo.feature.account.presentation.components.ConnectToServerCard
import de.davis.keygo.feature.account.presentation.models.AccountState
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent

@Composable
internal fun AccountContent(state: AccountState, onEvent: (AccountUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.account))
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(AccountUiEvent.OnBackClicked) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        SettingsList(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = innerPadding,
        ) {
            custom(key = "banner") {
                AccountBanner(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            section(title = R.string.settings_security) {
                action(
                    title = R.string.settings_reset_password,
                    icon = Icons.Default.LockReset,
                    supporting = ResourceString(
                        if (state is AccountState.Connected) R.string.settings_reset_password_w_server_description
                        else R.string.settings_reset_password_description
                    ),
                    onClick = { onEvent(AccountUiEvent.ResetPassword) },
                )
            }

            section(title = R.string.server) {
                custom(key = "server") {
                    ConnectToServerCard(
                        onScanInviteClicked = { onEvent(AccountUiEvent.OnScanInviteClicked) },
                        onConnectClicked = { onEvent(AccountUiEvent.OnConnectClicked) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AccountContentPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            AccountContent(state = AccountState.Local, onEvent = {})
        }
    }
}
