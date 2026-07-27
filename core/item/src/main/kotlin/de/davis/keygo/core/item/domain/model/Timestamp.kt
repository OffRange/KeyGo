package de.davis.keygo.core.item.domain.model

import kotlin.time.Clock
import kotlin.time.Instant

data class Timestamp(
    val createdAt: Instant = Clock.System.now(),
    val modifiedAt: Instant? = null,
)
