package de.davis.keygo.feature.autofill.presentation.activity.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.autofill.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SmsCodePendingDialog(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(painter = painterResource(R.drawable.outline_sms_24), contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.waiting_for_sms_code))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.waiting_for_sms_code_description))
                LoadingIndicator()
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
private fun SmsCodePendingDialogPreview() {
    MaterialTheme {
        SmsCodePendingDialog(
            onCancel = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
