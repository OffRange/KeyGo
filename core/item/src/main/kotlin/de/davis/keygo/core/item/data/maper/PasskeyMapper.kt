package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.domain.model.Passkey

internal fun Passkey.toData() = PasskeyEntity(
    credentialId = credentialId.toHexString(),
    privateKey = privateKey,
    passwordId = passwordId,
)

internal fun PasskeyEntity.toDomain() = Passkey(
    credentialId = credentialId.hexToByteArray(),
    privateKey = privateKey,
    passwordId = passwordId,
)