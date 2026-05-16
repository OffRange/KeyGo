package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.normalize

internal fun TagEntity.toDomain(): Tag = value

internal fun Tag.toData(): TagEntity = TagEntity(
    value = this,
    normalized = normalize(),
)

internal fun Iterable<Tag>.toTagEntities(): Set<TagEntity> =
    asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.toData() }
        .distinctBy { it.normalized }
        .toSet()
