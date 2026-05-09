package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.core.item.domain.model.PasskeyUser

internal fun Passkey.toData() = PasskeyEntity(
    credentialId = credentialId,
    rp = rp,
    privateKey = privateKey,
    loginId = loginId,
    name = user.name,
    displayName = user.displayName,
)

internal fun PasskeyEntity.toDomain() = Passkey(
    credentialId = credentialId,
    rp = rp,
    privateKey = privateKey,
    loginId = loginId,
    user = PasskeyUser(
        name = name,
        displayName = displayName
    )
)

internal fun PasskeyMetadataPojo.toDomain() = PasskeyMetadata(
    passwordUsername = pwdUsername,
    vaultName = vaultName,
    user = PasskeyUser(
        name = name,
        displayName = displayName
    ),
    credentialId = credentialId,
)