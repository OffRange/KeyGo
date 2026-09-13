package de.davis.keygo.core.identity.domain.mapper

import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData

fun CryptographicData.toBiometricWrappedArk() = BiometricWrappedArk(
    key = data,
    keyIV = iv,
)
