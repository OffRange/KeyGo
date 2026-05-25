package de.davis.keygo.feature.credit_card.presentation

import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberIsNfcAvailable(): Boolean {
    val context = LocalContext.current
    return remember { NfcAdapter.getDefaultAdapter(context) != null }
}
