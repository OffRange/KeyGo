package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.domain.model.Tag

internal fun TagEntity.toDomain(): Tag = Tag.of(value)!!

internal fun Tag.toData(): TagEntity = TagEntity(
    value = display,
    normalized = normalized,
)

internal fun Iterable<Tag>.toTagEntities(): Set<TagEntity> =
    asSequence()
        .map { it.toData() }
        .distinctBy { it.normalized }
        .toSet()
