package de.davis.keygo.feature.list_screen.domain.model

sealed interface VaultCreationError {
    object WrapFailed : VaultCreationError
    object BlankName : VaultCreationError
}
