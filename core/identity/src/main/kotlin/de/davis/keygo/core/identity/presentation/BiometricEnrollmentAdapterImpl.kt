package de.davis.keygo.core.identity.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import org.koin.compose.koinInject
import org.koin.core.annotation.Single

@Deprecated("Use EnableBiometricsUseCase and DisableBiometricsUseCase instead")
@Single
internal class BiometricEnrollmentAdapterImpl(
    private val accountRepository: AccountRepository,
    private val keyStoreManager: KeyStoreManager,
) : BiometricEnrollmentAdapter {

    @Deprecated("Use DisableBiometricsUseCase instead")
    override suspend fun disableBiometric(): Result<Unit, BiometricEnrollmentError> =
        resultBinding {
            val account = accountRepository.getOrNull()
                .asResult(BiometricEnrollmentError.NoActiveAccount).bind()

            accountRepository.set(account.copy(biometricWrappedArk = null))
                .bind { BiometricEnrollmentError.PersistenceFailed }

            keyStoreManager.deleteKey(KeyId.BiometricVaultKek)
        }
}

@Composable
fun rememberBiometricEnrollmentAdapter(): BiometricEnrollmentAdapter {
    return koinInject<BiometricEnrollmentAdapter>()
}