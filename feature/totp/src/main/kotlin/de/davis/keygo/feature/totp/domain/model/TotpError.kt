package de.davis.keygo.feature.totp.domain.model

import de.davisalessandro.keygo.rust.TotpException

sealed interface TotpError {
    data object UnsupportedAlgorithm : TotpError
    data object CryptoFailed : TotpError
    data class RustFailed(val cause: TotpException) : TotpError

    val message: String
        get() = when (this) {
            UnsupportedAlgorithm -> "Unsupported algorithm"
            CryptoFailed -> "Failed to decrypt secret"
            is RustFailed -> "Failed to generate TOTP: ${cause.message}"
        }
}