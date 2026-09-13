package de.davis.keygo.core.security.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.security.Key

@Deprecated("use :core:biometrics instead")
internal class BiometricCryptoControllerImpl : BiometricCryptoController {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun requestUnwrap(
        keyId: KeyId,
        ciphertextData: CiphertextData,
        policy: BiometricPolicy
    ): Result<Key, BiometricAuthError> = TODO()

}

@Deprecated("Use :core:biometrics instead")
@Composable
fun rememberBiometricCryptoController(): BiometricCryptoController {
    return remember {
        BiometricCryptoControllerImpl()
    }
}