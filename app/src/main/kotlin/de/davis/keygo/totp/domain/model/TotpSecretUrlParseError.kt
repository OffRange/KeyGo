package de.davis.keygo.totp.domain.model

sealed interface TotpSecretUrlParseError {
    data class CouldNotParseUrl(val url: String) : TotpSecretUrlParseError

    data class SchemeNotSupported(val scheme: String?) : TotpSecretUrlParseError
    data class HostNotSupported(val host: String?) : TotpSecretUrlParseError
    data class IssuerMismatch(val param: String, val query: String) : TotpSecretUrlParseError


    data object NoPathProvided : TotpSecretUrlParseError
    data object NoQueryProvided : TotpSecretUrlParseError
    data object NoSecretProvided : TotpSecretUrlParseError
}