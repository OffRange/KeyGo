package de.davis.keygo.core.security.domain.model

sealed interface CryptographicMode {
    data object Wrap : CryptographicMode
    data object Unwrap : CryptographicMode

    data object Encrypt : CryptographicMode
    data object Decrypt : CryptographicMode
}
