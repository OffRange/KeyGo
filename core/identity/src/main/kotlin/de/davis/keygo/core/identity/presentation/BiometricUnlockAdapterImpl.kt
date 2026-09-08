package de.davis.keygo.core.identity.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result
import org.koin.compose.koinInject
import org.koin.core.annotation.Single

@Single
internal class BiometricUnlockAdapterImpl(
    private val session: Session,
    private val accountRepository: AccountRepository,
    private val biometricEnrollmentAdapter: BiometricEnrollmentAdapter
) : BiometricUnlockAdapter {

    override suspend fun BiometricCryptoController.requestUnlockVault(
        policy: BiometricPolicy
    ): Result<Unit, UnlockError> {
        val wrappedKey = accountRepository.getOrNull()?.biometricWrappedArk
            ?: return Result.Failure(UnlockError.WrappedKeyNotFound)

        val unwrapResult = requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            ciphertextData = CiphertextData(
                bytes = wrappedKey.key,
                iv = wrappedKey.keyIV
            ),
            policy = policy
        )

        return when (unwrapResult) {
            is Result.Failure -> when (unwrapResult.error) {
                // The wrapped ARK cannot be opened by this key again, so keeping it would leave the
                // user tapping a biometric unlock that can never succeed. Dropping it falls the
                // account back to the password and lets a fresh enrollment mint a usable key.
                BiometricAuthError.KeyInvalidated -> {
                    biometricEnrollmentAdapter.disableBiometric()
                    Result.Failure(UnlockError.BiometricEnrollmentReset)
                }

                else -> Result.Failure(UnlockError.BiometricFailed(unwrapResult.error))
            }

            is Result.Success -> {
                session.startSession(unwrapResult.success.encoded)
                Result.Success(Unit)
            }
        }
    }
}

@Composable
fun rememberBiometricUnlockAdapter(): BiometricUnlockAdapter {
    val session = koinInject<Session>()
    val accountRepository = koinInject<AccountRepository>()
    val biometricEnrollmentAdapter = rememberBiometricEnrollmentAdapter()

    return remember(session, accountRepository, biometricEnrollmentAdapter) {
        BiometricUnlockAdapterImpl(
            session = session,
            accountRepository = accountRepository,
            biometricEnrollmentAdapter = biometricEnrollmentAdapter,
        )
    }
}