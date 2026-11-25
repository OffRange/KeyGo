package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.model.AccountChoice
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.SignInOrOutState

@Composable
internal fun SignInOrOutContent(
    state: SignInOrOutState,
    onEvent: (ConnectServerUiEvent) -> Unit,
) {
    AccountSetupScaffold(
        title = stringResource(R.string.sign_in_or_out_title),
        icon = Icons.Default.AccountCircle,
    ) {
        ServerSummary(state = state)

        Spacer(modifier = Modifier.height(24.dp))

        AccountOption(
            index = 0,
            icon = Icons.AutoMirrored.Default.Login,
            title = stringResource(R.string.sign_in),
            description = stringResource(R.string.sign_in_coming_soon),
            onClick = { onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.SignIn)) },
        )

        Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))

        AccountOption(
            index = 1,
            icon = Icons.Default.PersonAddAlt1,
            title = stringResource(R.string.create_account),
            description = stringResource(
                if (state.signupsOpen) R.string.create_account_description
                else R.string.create_account_invite_description,
            ),
            onClick = { onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.CreateAccount)) },
        )
    }
}

@Composable
private fun ServerSummary(state: SignInOrOutState) {
    val details = listOfNotNull(
        stringResource(R.string.sign_in_or_out_keygo_server),
        state.serverVersion?.let { stringResource(R.string.connect_server_version, it) },
        stringResource(
            if (state.signupsOpen) R.string.connect_server_open_signups
            else R.string.connect_server_invite_only,
        ),
    ).joinToString(separator = " \u2022 ")

    ListItem(
        supportingContent = { Text(text = details) },
        leadingContent = {
            IconBadge(
                icon = Icons.Default.VerifiedUser,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.serverHost,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountOption(
    index: Int,
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, ACCOUNT_OPTION_COUNT),
        supportingContent = { Text(text = description) },
        leadingContent = {
            IconBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(text = title)
    }
}

private const val ACCOUNT_OPTION_COUNT = 2

private class SignInOrOutStateProvider : PreviewParameterProvider<SignInOrOutState> {

    override val values = sequenceOf(
        SignInOrOutState(
            serverHost = "vault.example.org",
            serverVersion = "0.4.0",
            signupsOpen = true,
        ),
        SignInOrOutState(
            serverHost = "vault.example.org",
            serverVersion = "0.4.0",
            signupsOpen = false,
        ),
        SignInOrOutState(
            serverHost = "a-very-long-subdomain.vault.example.org",
            serverVersion = null,
            signupsOpen = true,
        ),
    )
}

@Preview
@Composable
private fun SignInOrOutContentPreview(
    @PreviewParameter(SignInOrOutStateProvider::class) state: SignInOrOutState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SignInOrOutContent(state = state, onEvent = {})
        }
    }
}
