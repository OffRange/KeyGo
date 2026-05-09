package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.domain.model.KeyInformation as DomainKeyInformation

internal fun DomainKeyInformation.toEntity(): KeyInformation = KeyInformation(
    wrappedKey = wrappedKey,
    keyNonce = keyNonce
)

internal fun KeyInformation.toDomain(): DomainKeyInformation = DomainKeyInformation(
    wrappedKey = wrappedKey,
    keyNonce = keyNonce
)