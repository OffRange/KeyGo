package de.davis.keygo.auth.domain.model

sealed interface CryptographicMode {
    data object Wrap : CryptographicMode
    data object Unwrap : CryptographicMode
}