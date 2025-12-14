package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.domain.model.Passkey

internal fun Passkey.toData() = PasskeyEntity(
    credentialId = credentialId,
    rp = rp,
    privateKey = privateKey,
    passwordId = passwordId,
)

internal fun PasskeyEntity.toDomain() = Passkey(
    credentialId = credentialId,
    rp = rp,
    privateKey = privateKey,
    passwordId = passwordId,
)