package de.davis.keygo.core.sync.domain.model

import java.net.URI
import java.util.UUID

sealed interface ServerLink {

    data class Connected(
        val userId: UUID,
        val serverUrl: String,
        val username: String,
    ) : ServerLink {

        val authority: String
            get() = runCatching { URI(serverUrl).authority }.getOrNull() ?: serverUrl
    }

    data object None : ServerLink
}
