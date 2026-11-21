package de.davis.keygo.core.sync.domain.model

import de.davis.keygo.core.identity.domain.model.VerifyPasswordError

sealed interface RegisterError {

    data object ActiveAccountNotFound : RegisterError
    data class Password(val error: VerifyPasswordError) : RegisterError
    data class Sync(val error: SyncError) : RegisterError
}
