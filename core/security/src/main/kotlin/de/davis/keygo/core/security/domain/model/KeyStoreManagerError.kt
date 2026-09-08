package de.davis.keygo.core.security.domain.model

sealed interface KeyStoreManagerError {

    data object KeyInvalidated : KeyStoreManagerError
    data object AuthenticationRequired : KeyStoreManagerError
    data object Unknown : KeyStoreManagerError
}
