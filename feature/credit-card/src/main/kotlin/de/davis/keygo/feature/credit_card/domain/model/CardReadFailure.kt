package de.davis.keygo.feature.credit_card.domain.model

sealed interface CardReadFailure {
    data object NotAnEmvCard : CardReadFailure
    data object TagLost : CardReadFailure
    data object NoReadableData : CardReadFailure
    data class Unexpected(val cause: Throwable) : CardReadFailure
}