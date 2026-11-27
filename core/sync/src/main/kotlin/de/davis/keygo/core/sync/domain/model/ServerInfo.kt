package de.davis.keygo.core.sync.domain.model

data class ServerInfo(
    val version: String,
    val publicUrl: String,
    val allowSignups: Boolean
)
