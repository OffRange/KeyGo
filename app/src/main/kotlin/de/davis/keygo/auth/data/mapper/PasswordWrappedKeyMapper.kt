package de.davis.keygo.auth.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.auth.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.auth.data.local.model.protoPasswordKeyData
import de.davis.keygo.auth.domain.model.PasswordWrappedKeyData

fun ProtoPasswordKeyData.toDomain() = PasswordWrappedKeyData(
    wrappedKey = key.toByteArray(),
    iv = keyIV.toByteArray(),
    salt = salt.toByteArray()
)

fun PasswordWrappedKeyData.toProto() = protoPasswordKeyData {
    key = wrappedKey.toByteString()
    keyIV = iv.toByteString()
    salt = this@toProto.salt.toByteString()
}