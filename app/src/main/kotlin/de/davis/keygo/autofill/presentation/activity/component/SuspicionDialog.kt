package de.davis.keygo.autofill.presentation.activity.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.R

@Composable
internal fun SuspicionDialog(
    onContinue: () -> Unit,
    onAbort: () -> Unit,
    appPackageName: String,
    website: String,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(
                onClick = onAbort
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onContinue
            ) {
                Text(text = stringResource(R.string.fill_anyway))
            }
        },
        icon = {
            Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.suspicious_activity))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.suspicious_activity_description,
                    appPackageName,
                    website
                )
            )
        },
        modifier = modifier
    )
}

@Preview
@Composable
private fun SuspicionDialogPreview() {
    MaterialTheme {
        SuspicionDialog(
            onContinue = {},
            onAbort = {},
            appPackageName = "com.example.app",
            website = "example.com",
            modifier = Modifier.fillMaxWidth()
        )
    }
}