package de.davis.keygo.feature.totp.domain.model

data class TotpValue(val code: String, val validUntil: Long, val maxLifetime: Long)
