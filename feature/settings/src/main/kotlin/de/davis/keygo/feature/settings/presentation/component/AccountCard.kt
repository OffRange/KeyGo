package de.davis.keygo.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.feature.settings.R
import de.davis.keygo.feature.settings.presentation.models.AccountState

private val AccountState.badgeColor: Color
    @Composable
    get() = if (this is AccountState.Connected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primaryContainer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountCard(state: AccountState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = onClick,
        modifier = modifier.heightIn(min = 84.dp),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            IconBadge(
                containerColor = state.badgeColor,
                shape = MaterialShapes.Cookie6Sided.toShape(),
                size = 56.dp,
            ) {
                if (state is AccountState.Connected) {
                    Text(
                        text = state.username.take(2).uppercase(),
                        color = contentColorFor(state.badgeColor)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = contentColorFor(state.badgeColor),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = if (state is AccountState.Connected) stringResource(
                    R.string.account_signed_in,
                    state.server
                )
                else stringResource(R.string.account_local_only)
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
            text = if (state is AccountState.Connected) state.username else stringResource(R.string.account),
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
            AccountCard(state = AccountState.Local, onClick = {})
            AccountCard(
                state = AccountState.Connected(
                    username = "ada",
                    server = "vault.example.com"
                ),
                onClick = {},
            )
        }
    }
}