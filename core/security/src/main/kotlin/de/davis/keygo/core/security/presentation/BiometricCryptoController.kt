package de.davis.keygo.core.security.presentation

import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import java.security.Key

@Deprecated("use :core:biometrics instead")
interface BiometricCryptoController {

    suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy = BiometricPolicy.Default
    ): Result<Key, BiometricAuthError>

}