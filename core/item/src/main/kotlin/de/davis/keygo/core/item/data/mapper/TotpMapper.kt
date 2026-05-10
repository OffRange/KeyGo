package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import de.davis.keygo.core.item.domain.model.Totp

internal fun TotpEntity.toDomain(): Totp = Totp(
    loginId = loginId,
    secret = Totp.Secret(secret),
    accountName = accountName,
    issuer = issuer,
    algorithm = algorithm,
    digits = digits,
    period = period,
)

internal fun Totp.toData(): TotpEntity = TotpEntity(
    loginId = loginId,
    secret = secret.payload,
    issuer = issuer,
    accountName = accountName,
    algorithm = algorithm,
    digits = digits,
    period = period,
)