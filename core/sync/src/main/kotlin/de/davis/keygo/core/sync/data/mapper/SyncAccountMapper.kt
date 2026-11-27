package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davisalessandro.keygo.rust.Account

internal fun Account.toDomain() = SyncAccount(
    userId = userId,
    username = username,
    deviceId = deviceId,
)
