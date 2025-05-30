package de.davis.keygo.auth.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.auth.data.local.model.protoBiometricKeyData
import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData

fun ProtoBiometricKeyData.toDomain() = BiometricWrappedKeyData(
    wrappedKey = key.toByteArray(),
    iv = keyIV.toByteArray()
)

fun BiometricWrappedKeyData.toProto() = protoBiometricKeyData {
    key = wrappedKey.toByteString()
    keyIV = iv.toByteString()
}