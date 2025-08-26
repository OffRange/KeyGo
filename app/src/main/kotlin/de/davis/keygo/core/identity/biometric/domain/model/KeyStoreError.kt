package de.davis.keygo.core.identity.biometric.domain.model

sealed interface KeyStoreError {
    data object KeyNotFound : KeyStoreError
}