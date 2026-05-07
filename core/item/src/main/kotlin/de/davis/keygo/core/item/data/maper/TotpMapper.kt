package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.TotpEntity
import de.davis.keygo.core.item.domain.model.Totp

internal fun TotpEntity.toDomain(): Totp = Totp(
    passwordId = passwordId,
    id = id,
    secret = secret,
    issuer = issuer,
    accountName = accountName,
    algorithm = algorithm,
    digits = digits,
    period = period,
)

internal fun Totp.toData(): TotpEntity = TotpEntity(
    passwordId = passwordId,
    id = id,
    secret = secret,
    issuer = issuer,
    accountName = accountName,
    algorithm = algorithm,
    digits = digits,
    period = period,
)