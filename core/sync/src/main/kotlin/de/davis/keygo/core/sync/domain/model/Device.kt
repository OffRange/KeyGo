package de.davis.keygo.core.sync.domain.model

import java.util.UUID
import kotlin.time.Instant

data class Device(
    val id: UUID,
    val name: String,
    val platform: String,
    val appVersion: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val authTime: Instant?,
    val loggedIn: Boolean,
    val current: Boolean,
)
