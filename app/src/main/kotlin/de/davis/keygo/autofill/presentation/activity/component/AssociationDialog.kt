package de.davis.keygo.autofill.presentation.activity.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.R

@Composable
internal fun AssociationDialog(
    itemName: String,
    domain: String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(text = stringResource(R.string.suggest))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.not_now))
            }
        },
        title = {
            Text(text = stringResource(R.string.suggest_for_this_app))
        },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.suggest_text,
                    if (domain.isNullOrBlank()) 0 else 1,
                    itemName,
                    domain.orEmpty(),
                )
            )
        },
        icon = {
            Icon(imageVector = Icons.Default.AutoMode, contentDescription = null)
        },
        modifier = Modifier.Companion.fillMaxWidth()
    )
}

@Preview
@Composable
private fun AssociationDialogPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AssociationDialog(
                itemName = "My Item",
                domain = "example.com",
                onDismissRequest = {},
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}