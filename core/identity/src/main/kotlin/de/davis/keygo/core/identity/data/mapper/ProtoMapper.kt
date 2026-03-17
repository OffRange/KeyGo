package de.davis.keygo.core.identity.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.identity.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.identity.data.local.model.protoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.protoPasswordKeyData
import de.davis.keygo.core.identity.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData

internal fun ProtoBiometricKeyData.toDomain() = BiometricWrappedKeyData(
    key = key.toByteArray(),
    keyIV = keyIV.toByteArray()
)

internal fun BiometricWrappedKeyData.toProto() = protoBiometricKeyData {
    key = this@toProto.key.toByteString()
    keyIV = this@toProto.keyIV.toByteString()
}

internal fun ProtoPasswordKeyData.toDomain() = PasswordWrappedKeyData(
    key = key.toByteArray(),
    keyIV = keyIV.toByteArray(),
    salt = salt.toByteArray()
)

internal fun PasswordWrappedKeyData.toProto() = protoPasswordKeyData {
    key = this@toProto.key.toByteString()
    keyIV = this@toProto.keyIV.toByteString()
    salt = this@toProto.salt.toByteString()
}