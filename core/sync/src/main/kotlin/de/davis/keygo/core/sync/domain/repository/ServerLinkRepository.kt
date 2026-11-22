package de.davis.keygo.core.sync.domain.repository

import de.davis.keygo.core.sync.domain.model.ServerLink
import kotlinx.coroutines.flow.Flow

interface ServerLinkRepository {

    fun observeLink(): Flow<ServerLink>
}
