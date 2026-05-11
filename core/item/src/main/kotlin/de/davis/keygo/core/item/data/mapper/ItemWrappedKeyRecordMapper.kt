package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.pojo.ItemWrappedKeyRecord
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope

internal fun ItemWrappedKeyRecord.toDomain() = ItemKeyEnvelope(
    vaultId = vaultId,
    itemId = itemId,
    itemKeyInformation = itemKeyInformation.toDomain(),
    vaultKeyInformation = vaultKeyInformation.toDomain(),
)