package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.VaultItemEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItem
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItemSearchResult
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult

internal fun VaultItem.toData() = VaultItemEntity(
    id = vaultItemId,
    name = name,
    note = note,
    encryptedData = encryptedData,
    itemType = itemType,
)

internal fun LightweightVaultItem.toDomain() = LiteItem.Concrete(
    vaultItemId = id,
    name = name,
    itemType = itemType,
)

internal fun LightweightVaultItemSearchResult.toDomain() = LiteVaultItemSearchResult(
    vaultItemId = id,
    name = name,
    itemType = itemType,
    matchedName = matchedName,
    matchedNote = matchedNote,
)
