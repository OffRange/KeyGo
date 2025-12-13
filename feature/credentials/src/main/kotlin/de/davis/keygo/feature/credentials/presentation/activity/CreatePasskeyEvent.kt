package de.davis.keygo.feature.credentials.presentation.activity

sealed interface CreatePasskeyEvent {
    data object Abort : CreatePasskeyEvent
    data object ShowList : CreatePasskeyEvent
    data class Finish(val responseJson: String) : CreatePasskeyEvent
}
