package de.davis.keygo.feature.item.create.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.core.ui.text.htmlStringResource
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.login.model.DialogState
import de.davis.keygo.feature.item.core.R as ItemCoreR

@Composable
fun DeletePasskeyDialog(
    state: DialogState.DeletePasskey,
    onConfirmDeletion: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.delete_passkey))
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
            )
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onConfirmDeletion) {
                Text(text = stringResource(ItemCoreR.string.delete))
            }
        },
        text = {
            Text(
                text = htmlStringResource(R.string.delete_passkey_warning, state.rpId)
            )
        },
    )
}

@Preview
@Composable
private fun DeletePasskeyDialogPreview() {
    KeyGoTheme {
        DeletePasskeyDialog(
            state = DialogState.DeletePasskey(rpId = "example.com"),
            onConfirmDeletion = {},
            onDismissRequest = {},
        )
    }
}
