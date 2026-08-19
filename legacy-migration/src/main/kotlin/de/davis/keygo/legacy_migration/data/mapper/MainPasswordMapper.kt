package de.davis.keygo.legacy_migration.data.mapper

import de.davis.keygo.legacy_migration.data.local.model.ProtoMainPassword
import de.davis.keygo.legacy_migration.domain.model.MainPassword
import java.time.Instant

internal fun ProtoMainPassword.toDomain() =
    MainPassword(hash, Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()))
