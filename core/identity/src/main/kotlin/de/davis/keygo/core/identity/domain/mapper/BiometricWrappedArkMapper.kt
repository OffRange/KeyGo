package de.davis.keygo.core.identity.domain.mapper

import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.security.domain.model.CiphertextData

fun CiphertextData.toBiometricWrappedArk() = BiometricWrappedArk(
    key = bytes,
    keyIV = iv,
)
