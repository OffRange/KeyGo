package de.davis.keygo.feature.account.presentation.mapper

import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.feature.account.presentation.models.AccountState
import java.net.URI

internal fun ServerLink?.toAccountState(): AccountState =
    if (this == null) AccountState.Local
    else AccountState.Connected(
        username = username,
        server = runCatching { URI(serverUrl).authority }.getOrNull() ?: serverUrl,
    )
