package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.data.local.model.ProtoSessionStore
import de.davis.keygo.core.sync.domain.model.ServerLink
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

internal fun ProtoSessionStore.toServerLink(): ServerLink =
    if (!hasSession()) ServerLink.None
    else ServerLink.Connected(
        userId = Uuid.fromByteArray(session.userId.toByteArray()).toJavaUuid(),
        serverUrl = session.serverUrl,
        username = session.username,
    )
