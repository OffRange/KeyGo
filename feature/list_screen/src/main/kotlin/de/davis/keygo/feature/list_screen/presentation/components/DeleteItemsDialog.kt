package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.list_screen.R

@Composable
internal fun DeleteItemsDialog(
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
        },
        title = {
            Text(
                text = pluralStringResource(
                    R.plurals.delete_items_title,
                    itemCount,
                    itemCount,
                )
            )
        },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.delete_items_message,
                    itemCount,
                    itemCount,
                )
            )
        },
    )
}
