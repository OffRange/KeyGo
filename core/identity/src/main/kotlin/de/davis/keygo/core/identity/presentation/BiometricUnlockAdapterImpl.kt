package de.davis.keygo.core.identity.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.asAesKey
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import org.koin.compose.koinInject
import org.koin.core.annotation.Single

@Single
internal class BiometricUnlockAdapterImpl(
    private val session: Session,
    private val wrappedKeyRepository: WrappedKeyRepository
) : BiometricUnlockAdapter {

    override suspend fun BiometricCryptoController.requestUnlockVault(): Result<Unit, UnlockError> {
        val wrappedKey = wrappedKeyRepository.getBiometricWrappedKey().getOrNull()
            ?: return Result.Failure(UnlockError.WrappedKeyNotFound)

        val result = requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            ciphertextData = CiphertextData(
                bytes = wrappedKey.key,
                iv = wrappedKey.keyIV
            ),
            policy = BiometricPolicy.Default
        ).getOrNull() ?: return Result.Failure(UnlockError.UnwrappingFailed)

        session.startSession(result.asAesKey())
        return Result.Success(Unit)
    }
}

@Composable
fun rememberBiometricUnlockAdapter(): BiometricUnlockAdapter {
    val session = koinInject<Session>()
    val wrappedKeyRepository = koinInject<WrappedKeyRepository>()

    return remember(session, wrappedKeyRepository) {
        BiometricUnlockAdapterImpl(
            session = session,
            wrappedKeyRepository = wrappedKeyRepository
        )
    }
}