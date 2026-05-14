package de.davis.keygo.core.security.domain.model

import de.davisalessandro.keygo.rust.KeyWrapException

sealed interface CryptoScopeError {
    data object IdNotFound : CryptoScopeError
    data class KeyWrapError(val exception: KeyWrapException) : CryptoScopeError
}
