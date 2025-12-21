package de.davis.keygo.feature.totp.domain.model

// TODO: store these information, so a finer generation is possible
data class TotpSecretInformation(
    val secret: String,
    val issuer: String?,
    val accountName: String,
    val algorithm: Algorithm,
    val digits: Int,
    val period: Int,
)
