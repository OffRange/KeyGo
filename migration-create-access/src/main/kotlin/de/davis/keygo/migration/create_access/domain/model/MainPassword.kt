package de.davis.keygo.migration.create_access.domain.model

import java.time.Instant

internal data class MainPassword(val hash: String, val createdAt: Instant)