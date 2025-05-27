package de.davis.keygo.core.data.mapper

import com.google.protobuf.timestamp
import de.davis.keygo.core.data.local.model.ProtoMainPassword
import de.davis.keygo.core.data.local.model.protoMainPassword
import de.davis.keygo.core.domain.model.MainPassword
import java.time.Instant

fun ProtoMainPassword.toDomain() =
    MainPassword(hash, Instant.ofEpochSecond(createdAt.seconds, createdAt.nanos.toLong()))

fun MainPassword.toProto(): ProtoMainPassword = protoMainPassword {
    hash = this@toProto.hash
    createdAt = timestamp {
        seconds = this@toProto.createdAt.epochSecond
        nanos = this@toProto.createdAt.nano
    }
}