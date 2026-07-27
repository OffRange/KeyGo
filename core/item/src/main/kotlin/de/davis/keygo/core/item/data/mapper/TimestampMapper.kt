package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.Timestamp
import kotlin.time.Instant
import de.davis.keygo.core.item.domain.model.Timestamp as DomainTimestamp

internal fun DomainTimestamp.toEntity(): Timestamp = Timestamp(
    createdAt = createdAt.toEpochMilliseconds(),
    modifiedAt = modifiedAt?.toEpochMilliseconds(),
)

internal fun Timestamp.toDomain(): DomainTimestamp = DomainTimestamp(
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    modifiedAt = modifiedAt?.let { Instant.fromEpochMilliseconds(it) },
)
