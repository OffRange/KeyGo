package de.davis.keygo.feature.account.presentation.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.presentation.password.PasswordConfirmDialog
import de.davis.keygo.feature.account.R
import de.davis.keygo.feature.account.presentation.models.DeleteAccountDialogState

@Composable
internal fun DeleteAccountDialog(
    state: DeleteAccountDialogState,
    password: TextFieldState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PasswordConfirmDialog(
        password = password,
        busy = state == DeleteAccountDialogState.Deleting,
        error = (state as? DeleteAccountDialogState.Confirming)?.error,
        onSubmit = onConfirm,
        onCancel = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = stringResource(R.string.delete_account_title)) },
        message = { Text(text = stringResource(R.string.delete_account_message)) },
        confirmButton = { enabled, onClick ->
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.delete_account_confirm))
            }
        },
    )
}

private class DeleteAccountDialogStateProvider :
    PreviewParameterProvider<DeleteAccountDialogState> {

    override val values = sequenceOf(
        DeleteAccountDialogState.Confirming(),
        DeleteAccountDialogState.Confirming(error = SyncError.InvalidCredentials),
        DeleteAccountDialogState.Deleting,
    )
}

@Preview
@Composable
private fun DeleteAccountDialogPreview(
    @PreviewParameter(DeleteAccountDialogStateProvider::class) state: DeleteAccountDialogState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DeleteAccountDialog(
                state = state,
                password = TextFieldState(),
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
