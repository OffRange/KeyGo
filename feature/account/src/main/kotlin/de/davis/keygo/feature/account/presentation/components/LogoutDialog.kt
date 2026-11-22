package de.davis.keygo.feature.account.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.models.LogoutDialogState

@Composable
internal fun LogoutDialog(
    state: LogoutDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val loggingOut = state == LogoutDialogState.LoggingOut
    val failed = state == LogoutDialogState.Failed

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !loggingOut,
            dismissOnClickOutside = !loggingOut,
        ),
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loggingOut) {
                Text(text = stringResource(if (failed) R.string.logout_retry else R.string.sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loggingOut) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        icon = {
            Icon(imageVector = Icons.AutoMirrored.Default.Logout, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.logout_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.logout_message))

                if (loggingOut)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                if (failed) Text(
                    text = stringResource(R.string.logout_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

private class LogoutDialogStateProvider : PreviewParameterProvider<LogoutDialogState> {

    override val values = LogoutDialogState.entries
        .filter { it != LogoutDialogState.Hidden }
        .asSequence()
}

@Preview
@Composable
private fun LogoutDialogPreview(
    @PreviewParameter(LogoutDialogStateProvider::class) state: LogoutDialogState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LogoutDialog(
                state = state,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
