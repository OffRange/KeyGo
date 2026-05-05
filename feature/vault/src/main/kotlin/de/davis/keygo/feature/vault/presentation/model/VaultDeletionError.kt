package de.davis.keygo.feature.vault.presentation.model

sealed interface VaultDeletionError {
    data object NameDoesNotMatch : VaultDeletionError
}