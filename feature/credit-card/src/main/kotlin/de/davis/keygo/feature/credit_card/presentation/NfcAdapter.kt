package de.davis.keygo.feature.credit_card.presentation

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


@Composable
internal fun ObserveCardReaderEvents(onEvent: suspend (IsoDepTransport?) -> Unit) {
    val activity = LocalActivity.current!!
    val context = LocalContext.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    ObserveAsEvents(nfcAdapter.tagFlow(activity), activity) { tag ->
        val transport = IsoDep.get(tag)?.let(::IsoDepTransport)
        onEvent(transport)
    }
}

private fun NfcAdapter.tagFlow(activity: Activity): Flow<Tag> = callbackFlow {
    val callback = NfcAdapter.ReaderCallback { tag -> trySend(tag) }
    val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK // payment cards aren't NDEF; skip the probe

    val extras = Bundle().apply {
        putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
    }

    enableReaderMode(activity, callback, flags, extras)
    awaitClose { disableReaderMode(activity) }
}
