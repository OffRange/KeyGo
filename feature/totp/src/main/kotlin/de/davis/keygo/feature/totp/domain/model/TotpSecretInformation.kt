package de.davis.keygo.feature.totp.domain.model

data class TotpSecretInformation(
    val secret: String,
    val issuer: String?,
    val accountName: String,
    val algorithm: Algorithm,
    val digits: Int,
    val period: Int,
)
