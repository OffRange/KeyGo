package de.davis.keygo.migration.create_access.domain.model

import java.time.Instant

data class MainPassword(val hash: String, val createdAt: Instant)