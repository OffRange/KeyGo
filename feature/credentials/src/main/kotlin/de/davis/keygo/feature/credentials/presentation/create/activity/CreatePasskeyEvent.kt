package de.davis.keygo.feature.credentials.presentation.create.activity

internal sealed interface CreatePasskeyEvent {
    data object Abort : CreatePasskeyEvent
    data object ShowList : CreatePasskeyEvent
    data class Finish(val responseJson: String) : CreatePasskeyEvent
}
