package de.davis.keygo.core.identity.common.domain.model

sealed interface CryptographicMode {
    data object Wrap : CryptographicMode
    data object Unwrap : CryptographicMode
}