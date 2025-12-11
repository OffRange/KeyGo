package de.davis.keygo.core.identity.biometric.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.auth.data.local.model.protoBiometricKeyData
import de.davis.keygo.core.identity.common.domain.model.BiometricWrappedKeyData

@Deprecated("Migrate to :core:security")
fun ProtoBiometricKeyData.toDomain() = BiometricWrappedKeyData(
    wrappedKey = key.toByteArray(),
    iv = keyIV.toByteArray()
)

@Deprecated("Migrate to :core:security")
fun BiometricWrappedKeyData.toProto() = protoBiometricKeyData {
    key = wrappedKey.toByteString()
    keyIV = iv.toByteString()
}