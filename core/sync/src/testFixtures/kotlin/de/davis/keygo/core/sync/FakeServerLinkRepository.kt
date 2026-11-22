package de.davis.keygo.core.sync

import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.core.sync.domain.repository.ServerLinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeServerLinkRepository(initial: ServerLink = ServerLink.None) : ServerLinkRepository {

    val link = MutableStateFlow(initial)

    override fun observeLink(): Flow<ServerLink> = link
}
