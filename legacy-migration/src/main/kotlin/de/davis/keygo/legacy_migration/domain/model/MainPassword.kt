package de.davis.keygo.legacy_migration.domain.model

import java.time.Instant

internal data class MainPassword(val hash: String, val createdAt: Instant)
