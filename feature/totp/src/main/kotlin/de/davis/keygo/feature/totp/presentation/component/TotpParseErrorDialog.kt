package de.davis.keygo.feature.totp.presentation.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.totp.R

@Composable
fun TotpParseErrorDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.ok))
            }
        },
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.totp_parse_error))
        },
        text = {
            Text(text = stringResource(R.string.totp_parse_error_description))
        },
    )
}