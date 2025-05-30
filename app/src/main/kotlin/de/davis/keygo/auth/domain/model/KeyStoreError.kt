package de.davis.keygo.auth.domain.model

sealed interface KeyStoreError {
    data object KeyNotFound : KeyStoreError
}