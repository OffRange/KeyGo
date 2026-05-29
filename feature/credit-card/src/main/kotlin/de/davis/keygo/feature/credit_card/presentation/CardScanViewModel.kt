package de.davis.keygo.feature.credit_card.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.credit_card.domain.ApduTransport
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import de.davis.keygo.feature.credit_card.domain.repository.CardReaderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CardScanViewModel(
    private val cardReaderRepository: CardReaderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CardScanUiState>(CardScanUiState.Ready)
    val state: StateFlow<CardScanUiState> = _state.asStateFlow()

    private val cardReadChannel = Channel<Card>()
    val cardRead = cardReadChannel.receiveAsFlow()

    private var readJob: Job? = null

    fun onTransport(transport: ApduTransport?) {
        // Ignore new tags while a read is in progress or while the success animation plays.
        val current = _state.value
        if (current is CardScanUiState.Reading || current is CardScanUiState.Success) return
        if (transport == null) {
            _state.value = CardScanUiState.Failure(CardReadFailure.NotAnEmvCard)
            return
        }

        _state.value = CardScanUiState.Reading
        readJob = viewModelScope.launch {
            cardReaderRepository.read(transport).fold(
                onSuccess = { card ->
                    _state.value = CardScanUiState.Success(card)

                    // We have a little delay to finish the animation
                    delay(500)
                    cardReadChannel.send(card)
                },
                onFailure = { _state.value = CardScanUiState.Failure(it) },
            )
        }
    }

    fun reset() {
        readJob?.cancel()
        readJob = null
        _state.value = CardScanUiState.Ready
    }
}
