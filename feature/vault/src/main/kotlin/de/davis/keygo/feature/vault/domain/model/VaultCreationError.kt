package de.davis.keygo.feature.vault.domain.model

sealed interface VaultCreationError {
    object WrapFailed : VaultCreationError
    object BlankName : VaultCreationError
}
