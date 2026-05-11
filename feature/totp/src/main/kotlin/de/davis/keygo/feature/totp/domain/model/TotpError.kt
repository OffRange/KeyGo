package de.davis.keygo.feature.totp.domain.model

sealed interface TotpError {
    data object NoLoginFound : TotpError
    data object NoItemKeyInfoFound : TotpError
}