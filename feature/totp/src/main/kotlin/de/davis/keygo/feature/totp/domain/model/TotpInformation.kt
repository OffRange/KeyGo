package de.davis.keygo.feature.totp.domain.model

data class TotpInformation(val code: String, val validUntil: Long, val maxLifetime: Long)
