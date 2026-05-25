package de.davis.keygo.feature.credit_card.presentation

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure

@Stable
internal sealed interface CardScanUiState {
    data object Ready : CardScanUiState
    data object Reading : CardScanUiState
    data class Success(val card: Card) : CardScanUiState
    data class Failure(val reason: CardReadFailure) : CardScanUiState
}
