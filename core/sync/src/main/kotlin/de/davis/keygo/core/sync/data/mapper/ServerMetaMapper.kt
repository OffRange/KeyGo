package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davisalessandro.keygo.rust.ServerInfo as RustServerInfo

internal fun RustServerInfo.toKotlin() = ServerInfo(
    version = version,
    publicUrl = publicUrl,
    allowSignups = allowSignups
)
