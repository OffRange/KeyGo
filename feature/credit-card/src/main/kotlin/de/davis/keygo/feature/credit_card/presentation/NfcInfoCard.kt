package de.davis.keygo.feature.credit_card.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.credit_card.R
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NfcInfoCard(
    state: CardScanUiState,
    nfcEnabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            CardScanUiState.Ready -> {
                Icon(
                    imageVector = Icons.Default.Contactless,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (nfcEnabled) {
                    Text(
                        text = stringResource(R.string.card_scan_ready_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.card_scan_ready_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.card_scan_nfc_disabled_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    ) {
                        Text(text = stringResource(R.string.card_scan_enable_nfc))
                    }
                }
            }

            CardScanUiState.Reading -> {
                CircularWavyProgressIndicator()
                Text(
                    text = stringResource(R.string.card_scan_reading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is CardScanUiState.Success -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.card_scan_success),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is CardScanUiState.Failure -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(state.reason.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.card_scan_retry))
                }
            }
        }
    }
}

private fun CardReadFailure.messageRes(): Int = when (this) {
    CardReadFailure.NotAnEmvCard -> R.string.card_scan_error_not_emv
    CardReadFailure.TagLost -> R.string.card_scan_error_tag_lost
    CardReadFailure.NoReadableData -> R.string.card_scan_error_no_data
    is CardReadFailure.Unexpected -> R.string.card_scan_error_unexpected
}
