package de.davis.keygo.feature.totp.domain.model

sealed interface TotpError {
    data object UnsupportedAlgorithm : TotpError
    data object CryptoFailed : TotpError
}