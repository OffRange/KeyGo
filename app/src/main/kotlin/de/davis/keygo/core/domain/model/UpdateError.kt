package de.davis.keygo.core.domain.model

sealed interface UpdateError {
    data object VaultItemNotFound : UpdateError
}