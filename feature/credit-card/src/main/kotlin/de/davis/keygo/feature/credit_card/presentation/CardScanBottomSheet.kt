package de.davis.keygo.feature.credit_card.presentation

import android.nfc.NfcAdapter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.BroadcastReceiver
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.credit_card.domain.model.Card
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScanBottomSheet(
    onCardRead: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: CardScanViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var nfcEnabled by remember { mutableStateOf(false) }
    BroadcastReceiver(
        action = NfcAdapter.ACTION_ADAPTER_STATE_CHANGED,
        flags = ContextCompat.RECEIVER_EXPORTED,
    ) {
        nfcEnabled = it?.getIntExtra(
            NfcAdapter.EXTRA_ADAPTER_STATE,
            NfcAdapter.STATE_OFF
        ) == NfcAdapter.STATE_ON
    }

    val dismiss = {
        viewModel.reset()
        onDismiss()
    }

    ObserveCardReaderEvents(viewModel::onTransport)

    // Drop any terminal state / in-flight read left over from a previous open in this screen
    // session; cardRead only emits for a read completed during THIS open.
    LaunchedEffect(Unit) { viewModel.reset() }

    ObserveAsEvents(viewModel.cardRead) { card ->
        onCardRead(card)
        dismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        NfcInfoCard(
            state = state,
            nfcEnabled = nfcEnabled,
            onRetry = viewModel::reset,
        )
    }
}
