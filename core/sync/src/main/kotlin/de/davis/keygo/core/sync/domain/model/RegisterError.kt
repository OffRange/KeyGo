package de.davis.keygo.core.sync.domain.model

sealed interface RegisterError {

    data object ActiveAccountNotFound : RegisterError
    data class Sync(val error: SyncError) : RegisterError
}
