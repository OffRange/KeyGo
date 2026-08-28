package de.davis.keygo.feature.credit_card.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.feature.credit_card.R
import de.davis.keygo.feature.credit_card.domain.model.Card

@Composable
fun CardScanEntry(
    onCardRead: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hardware-presence check only; the sheet handles the NFC-disabled case live.
    if (!rememberIsNfcAvailable()) return

    var showScanSheet by remember { mutableStateOf(false) }

    ScanCardPrompt(onClick = { showScanSheet = true }, modifier = modifier)

    if (showScanSheet)
        CardScanBottomSheet(
            onCardRead = onCardRead,
            onDismiss = { showScanSheet = false },
        )
}

@Composable
private fun ScanCardPrompt(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyGoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        properties = KeyGoCardProperties.elevated(),
        leadingItem = {
            Icon(
                imageVector = Icons.Default.Contactless,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.card_scan_entry_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        trailingItem = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.card_scan_entry_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun ScanCardPromptPreview() {
    MaterialTheme {
        Surface {
            ScanCardPrompt(onClick = {})
        }
    }
}
