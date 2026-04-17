package de.davis.keygo.core.identity.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.identity.data.local.model.ProtoAccount
import de.davis.keygo.core.identity.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.identity.data.local.model.biometricKeyDataOrNull
import de.davis.keygo.core.identity.data.local.model.protoAccount
import de.davis.keygo.core.identity.data.local.model.protoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.protoPasswordKeyData
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import java.util.UUID

internal fun ProtoBiometricKeyData.toDomain() = BiometricWrappedArk(
    key = key.toByteArray(),
    keyIV = keyIV.toByteArray()
)

internal fun BiometricWrappedArk.toProto() = protoBiometricKeyData {
    key = this@toProto.key.toByteString()
    keyIV = this@toProto.keyIV.toByteString()
}

internal fun ProtoPasswordKeyData.toDomain() = PasswordWrappedArk(
    key = key.toByteArray(),
    keyIV = keyIV.toByteArray(),
    salt = salt.toByteArray()
)

internal fun PasswordWrappedArk.toProto() = protoPasswordKeyData {
    key = this@toProto.key.toByteString()
    keyIV = this@toProto.keyIV.toByteString()
    salt = this@toProto.salt.toByteString()
}

internal fun ProtoAccount.toDomain() = Account(
    id = UUID.fromString(userId),
    displayName = displayName,
    createdAtEpochMillis = createdAtEpochMillis,
    passwordWrappedArk = passwordKeyData.toDomain(),
    biometricWrappedArk = biometricKeyDataOrNull?.toDomain(),
)

internal fun Account.toProto() = protoAccount {
    userId = this@toProto.id.toString()
    displayName = this@toProto.displayName
    createdAtEpochMillis = this@toProto.createdAtEpochMillis
}