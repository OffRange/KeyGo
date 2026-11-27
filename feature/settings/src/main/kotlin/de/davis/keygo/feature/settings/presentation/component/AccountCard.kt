package de.davis.keygo.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.feature.account.presentation.components.AccountIconBadge
import de.davis.keygo.feature.settings.R
import java.util.UUID
import de.davis.keygo.feature.account.R as AccountR

@Composable
internal fun AccountCard(state: ServerLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = onClick,
        modifier = modifier.heightIn(min = 84.dp),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            AccountIconBadge(
                state = state,
                badgeSize = 56.dp,
                iconSize = 22.dp,
            )
        },
        supportingContent = {
            Text(
                text = if (state is ServerLink.Connected) stringResource(
                    R.string.account_signed_in,
                    state.authority
                )
                else stringResource(AccountR.string.account_local_only)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Default.NavigateNext,
                contentDescription = null,
            )
        },
    ) {
        Text(
            text = if (state is ServerLink.Connected) state.username else stringResource(AccountR.string.account),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview
@Composable
private fun AccountCardPreview() {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccountCard(state = ServerLink.None, onClick = {})
            AccountCard(
                state = ServerLink.Connected(
                    userId = UUID.randomUUID(),
                    serverUrl = "https://vault.example.com/",
                    username = "ada",
                ),
                onClick = {},
            )
        }
    }
}