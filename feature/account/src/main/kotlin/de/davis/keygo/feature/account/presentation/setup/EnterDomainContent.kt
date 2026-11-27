package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.core.ui.text.transformation.rememberSchemeStrippingTransformation
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.setup.components.AccountSetupScaffold
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.EnterDomainState
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.core.sync.R as SyncR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EnterDomainContent(
    state: EnterDomainState,
    onEvent: (ConnectServerUiEvent) -> Unit,
) {
    val error = state.check.message
    val schemeTransformation = rememberSchemeStrippingTransformation {
        onEvent(ConnectServerUiEvent.SchemeChanged(it))
    }
    // Whitespace goes first so a pasted "  https://…" still has its scheme recognised.
    val inputTransformation = remember(schemeTransformation) {
        WhitespaceStrippingTransformation.then(schemeTransformation)
    }

    AccountSetupScaffold(
        title = stringResource(R.string.connect_server_which_title),
        description = stringResource(R.string.connect_server_which_description),
        icon = Icons.Default.Language,
    ) {
        OutlinedTextField(
            state = state.addressTextFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = stringResource(R.string.server_address))
            },
            placeholder = {
                Text(text = stringResource(R.string.server_address_placeholder))
            },
            inputTransformation = inputTransformation,
            prefix = {
                Text(
                    text = state.typedScheme ?: stringResource(R.string.server_address_prefix),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                when (state.check) {
                    ServerCheck.Checking -> LoadingIndicator(modifier = Modifier.size(24.dp))
                    is ServerCheck.Found -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    is ServerCheck.Failed -> IconButton(onClick = { onEvent(ConnectServerUiEvent.Retry) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.connect_server_retry),
                        )
                    }

                    ServerCheck.InvalidAddress,
                    ServerCheck.NotAnOrigin,
                    ServerCheck.Idle -> Unit
                }
            },
            supportingText = {
                Text(
                    text = error ?: stringResource(R.string.connect_server_address_helper),
                )
            },
            isError = error != null,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            onKeyboardAction = {
                when {
                    state.check is ServerCheck.Found -> onEvent(ConnectServerUiEvent.Continue)
                    state.check is ServerCheck.Failed -> onEvent(ConnectServerUiEvent.Retry)
                }
            },
        )

        val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state.check as? ServerCheck.Found,
            modifier = Modifier.padding(top = 16.dp),
            transitionSpec = { fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) },
            label = "server_found",
        ) { found ->
            if (found != null) ServerFoundCard(found = found)
            else Box(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ServerFoundCard(found: ServerCheck.Found) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Default.VerifiedUser,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.connect_server_found),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ServerFact(
                        text = stringResource(
                            R.string.connect_server_version,
                            found.version
                        )
                    )
                    ServerFact(
                        text = stringResource(
                            if (found.inviteOnly) R.string.connect_server_invite_only
                            else R.string.connect_server_open_signups,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerFact(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Drops whitespace instead of rejecting the edit, so a pasted "vault.example.org\n" still lands. */
private object WhitespaceStrippingTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        for (i in length - 1 downTo 0)
            if (charAt(i).isWhitespace()) replace(i, i + 1, "")
    }
}

private val ServerCheck.message: String?
    @Composable
    get() = when (this) {
        is ServerCheck.Failed -> stringResource(error.messageRes)
        ServerCheck.InvalidAddress -> stringResource(SyncR.string.sync_error_invalid_address)
        ServerCheck.NotAnOrigin -> stringResource(R.string.connect_server_address_not_an_origin)
        ServerCheck.Idle,
        ServerCheck.Checking,
        is ServerCheck.Found,
            -> null
    }

private class ServerCheckProvider : PreviewParameterProvider<ServerCheck> {

    override val values = sequenceOf(
        ServerCheck.Idle,
        ServerCheck.Checking,
        ServerCheck.Found(
            serverUrl = "https://vault.example.org",
            version = "0.4.0",
            publicUrl = "https://vault.example.org",
            inviteOnly = true,
        ),
        ServerCheck.Failed(SyncError.Network(NetworkFailure.Timeout)),
        ServerCheck.InvalidAddress,
        ServerCheck.NotAnOrigin,
    )
}

@Preview
@Composable
private fun EnterDomainContentPreview(
    @PreviewParameter(ServerCheckProvider::class) check: ServerCheck,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EnterDomainContent(
                state = EnterDomainState(
                    addressTextFieldState = TextFieldState(
                        if (check is ServerCheck.Idle) "" else "vault.example.org",
                    ),
                    check = check,
                ),
                onEvent = {},
            )
        }
    }
}
