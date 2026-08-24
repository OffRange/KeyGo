package de.davis.keygo.feature.totp.presentation.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.totp.R

/**
 * @param onDismissRequest what a back press (or outside tap) does. Defaults to doing nothing,
 * because the scanner's dialog sits inside a screen the user can still use, so a stray back press
 * should not dismiss it. The deep link gate's dialog is the whole screen, so back has to be a real
 * exit and passes [onDismiss] here too.
 */
@Composable
fun TotpParseErrorDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
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