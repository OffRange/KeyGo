package de.davis.keygo.core.data.mapper

import com.google.protobuf.Timestamp
import de.davis.keygo.core.data.local.model.ProtoMainPassword
import de.davis.keygo.core.domain.model.MainPassword
import java.time.Instant

fun ProtoMainPassword.toDomain() =
    MainPassword(hash, Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()))

fun MainPassword.toProto(): ProtoMainPassword = ProtoMainPassword.newBuilder().apply {
    hash = this@toProto.hash
    setCreatedAt(
        Timestamp.newBuilder()
            .setSeconds(this@toProto.createdAt.epochSecond)
            .setNanos(this@toProto.createdAt.nano)
    )
}.build()