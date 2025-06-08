package de.davis.keygo.migration.create_access.data.mapper

import de.davis.keygo.migration.create_access.data.local.model.ProtoMainPassword
import de.davis.keygo.migration.create_access.domain.model.MainPassword
import java.time.Instant

internal fun ProtoMainPassword.toDomain() =
    MainPassword(hash, Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()))
