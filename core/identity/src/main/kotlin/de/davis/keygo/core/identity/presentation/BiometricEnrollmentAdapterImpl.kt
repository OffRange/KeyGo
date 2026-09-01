package de.davis.keygo.core.identity.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import org.koin.compose.koinInject
import org.koin.core.annotation.Single
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Single
internal class BiometricEnrollmentAdapterImpl(
    private val accountRepository: AccountRepository,
    private val session: Session,
) : BiometricEnrollmentAdapter {

    override suspend fun BiometricCryptoController.requestEnableBiometric(
        policy: BiometricPolicy
    ): Result<Unit, BiometricEnrollmentError> = resultBinding {
        val account = accountRepository.getOrNull()
            .asResult(BiometricEnrollmentError.NoActiveAccount).bind()

        val cipher = requestCipher(KeyId.BiometricVaultKek, CryptographicMode.Wrap, policy)
            .bind { BiometricEnrollmentError.BiometricFailed(it) }


        val ark = session.ark.asResult(BiometricEnrollmentError.NoActiveSession).bind()

        val wrapped = wrapArk(ark, cipher)
            .asResult(BiometricEnrollmentError.WrappingFailed).bind()

        accountRepository.set(account.copy(biometricWrappedArk = wrapped)).bind {
            BiometricEnrollmentError.PersistenceFailed
        }
    }

    override suspend fun disableBiometric(): Result<Unit, BiometricEnrollmentError> =
        resultBinding {
            val account = accountRepository.getOrNull()
                .asResult(BiometricEnrollmentError.NoActiveAccount).bind()

            accountRepository.set(account.copy(biometricWrappedArk = null))
                .bind { BiometricEnrollmentError.PersistenceFailed }
        }

    private fun wrapArk(ark: ByteArray, cipher: Cipher): BiometricWrappedArk? = runCatching {
        BiometricWrappedArk(
            key = cipher.wrap(SecretKeySpec(ark, 0, ark.size, "AES")),
            keyIV = cipher.iv,
        )
    }.getOrNull()
}

@Composable
fun rememberBiometricEnrollmentAdapter(): BiometricEnrollmentAdapter {
    return koinInject<BiometricEnrollmentAdapter>()
}