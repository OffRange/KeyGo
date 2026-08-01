package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.data.local.pojo.MovableItemPojo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult

internal fun Item.toData() = ItemEntity(
    id = id,
    vaultId = vaultId,
    name = name,
    note = note,
    itemType = itemType,
    pinned = pinned,

    keyInformation = keyInformation.toEntity(),
    timestamp = timestamp.toEntity(),
)

internal fun LightweightItem.toDomain() = LiteItem.Concrete(
    id = id,
    name = name,
    itemType = itemType,
    pinned = pinned,
)

internal fun LightweightItemSearchResult.toDomain() = LiteItemSearchResult(
    id = id,
    name = name,
    itemType = itemType,
    matchedName = matchedName,
    matchedNote = matchedNote,
    matchedTag = matchedTag,
    pinned = pinned,
)

internal fun MovableItemPojo.toDomain() = MovableItem(
    id = id,
    keyInformation = keyInformation.toDomain(),
)
