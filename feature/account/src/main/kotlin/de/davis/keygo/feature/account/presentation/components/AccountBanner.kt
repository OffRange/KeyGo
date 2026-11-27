package de.davis.keygo.feature.account.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.preview.ServerLinkProvider

@Composable
fun AccountBanner(state: ServerLink, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccountIconBadge(
            state = state,
            badgeSize = 96.dp,
            iconSize = 40.dp,
        )

        Text(
            text = when (state) {
                ServerLink.None -> stringResource(R.string.account_local_only)
                is ServerLink.Connected -> state.username
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            text = when (state) {
                ServerLink.None -> stringResource(R.string.account_not_connected)
                is ServerLink.Connected -> state.authority
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview
@Composable
private fun AccountBannerPreview(
    @PreviewParameter(ServerLinkProvider::class) state: ServerLink,
) {
    MaterialTheme {
        Surface {
            AccountBanner(
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
