package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.security.domain.model.CryptoScopeError

sealed interface LoginError {
    data object BlankName : LoginError
    data object EmptyLogin : LoginError
    data object InvalidVaultId : LoginError
    data object InvalidItemId : LoginError
    data class CryptoError(val error: CryptoScopeError) : LoginError
    data class DatabaseError(val throwable: Throwable) : LoginError
}
