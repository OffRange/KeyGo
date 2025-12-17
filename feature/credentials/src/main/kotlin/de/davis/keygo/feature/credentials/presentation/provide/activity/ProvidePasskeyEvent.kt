package de.davis.keygo.feature.credentials.presentation.provide.activity

internal sealed interface ProvidePasskeyEvent {
    data object Abort : ProvidePasskeyEvent
    data class Finish(val responseJson: String) : ProvidePasskeyEvent
}