package de.davis.keygo.core.sync.domain.model

import java.util.UUID

data class ServerLink(
    val userId: UUID,
    val serverUrl: String,
    val username: String,
)
