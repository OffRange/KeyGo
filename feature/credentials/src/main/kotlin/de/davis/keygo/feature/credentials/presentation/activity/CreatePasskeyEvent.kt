package de.davis.keygo.feature.credentials.presentation.activity

sealed interface CreatePasskeyEvent {
    data object Abort : CreatePasskeyEvent
}