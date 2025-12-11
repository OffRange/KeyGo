package de.davis.keygo.core.identity.biometric.domain.model

@Deprecated("Migrate to :core:security")
sealed interface KeyStoreError {
    data object KeyNotFound : KeyStoreError
}