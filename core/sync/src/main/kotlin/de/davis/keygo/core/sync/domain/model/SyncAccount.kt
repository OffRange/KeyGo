package de.davis.keygo.core.sync.domain.model

import java.util.UUID

data class SyncAccount(
    val userId: UUID,
    val username: String,
    val deviceId: UUID,
)
