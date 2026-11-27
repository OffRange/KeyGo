package de.davis.keygo.feature.account.presentation.setup.mapper

import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.domain.model.ServerAddressError
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck

internal fun ServerInfo.toFound(address: ServerAddress) = ServerCheck.Found(
    serverUrl = address.origin,
    version = version,
    publicUrl = publicUrl,
    inviteOnly = !allowSignups,
)

internal fun ServerAddressError.toServerCheck(): ServerCheck = when (this) {
    ServerAddressError.Empty -> ServerCheck.Idle
    ServerAddressError.Invalid -> ServerCheck.InvalidAddress
    ServerAddressError.NotAnOrigin -> ServerCheck.NotAnOrigin
}
