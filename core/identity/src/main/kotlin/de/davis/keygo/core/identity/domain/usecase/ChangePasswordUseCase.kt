package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.BiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.ResultBinding
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
class ChangePasswordUseCase(
    private val biometricCrypto: BiometricCrypto,
    private val accountRepository: AccountRepository,
    private val session: Session,
) {

    suspend operator fun invoke(
        reauthentication: Reauthentication,
        newPassword: String,
    ): Result<Unit, ChangePasswordError> = resultBinding {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(ChangePasswordError.ActiveAccountNotFound)

        when (reauthentication) {
            is Reauthentication.Password -> passwordAuthenticationPath(reauthentication, account)
            is Reauthentication.Biometric -> biometricAuthenticationPath(account)
        }

        val rewrapped = session.rewrapForNewPassword(newPassword, account.id).bind {
            when (it) {
                is SessionError.Derivation -> ChangePasswordError.KeyDerivationFailed
                SessionError.Locked -> ChangePasswordError.ActiveAccountNotFound
                else -> ChangePasswordError.WrappingFailed
            }
        }

        accountRepository.set(
            account.copy(
                passwordWrappedArk = PasswordWrappedArk(
                    key = rewrapped.wrapped.ciphertext,
                    keyIV = rewrapped.wrapped.nonce,
                    salt = rewrapped.salt,
                ),
            ),
        ).bind { ChangePasswordError.PersistenceFailed }
    }

    private suspend fun ResultBinding<ChangePasswordError>.passwordAuthenticationPath(
        reauthentication: Reauthentication.Password,
        account: Account,
    ) {
        session.verifyPassword(
            password = reauthentication.currentPassword,
            salt = account.passwordWrappedArk.salt,
            wrapped = WrappedKeyBlob(
                ciphertext = account.passwordWrappedArk.key,
                nonce = account.passwordWrappedArk.keyIV,
            ),
            userId = account.id,
        ).bind {
            when (it) {
                is SessionError.Derivation -> ChangePasswordError.KeyDerivationFailed
                SessionError.Locked -> ChangePasswordError.ActiveAccountNotFound
                else -> ChangePasswordError.IncorrectPassword
            }
        }
    }

    private suspend fun ResultBinding<ChangePasswordError>.biometricAuthenticationPath(
        account: Account,
    ) {
        val wrappedKey = account.biometricWrappedArk
            .asResult(ChangePasswordError.BiometricNotEnrolled)
            .bind()

        var unwrappedArk: ByteArray? = null

        try {
            unwrappedArk = biometricCrypto.requestUnwrap(
                keyId = KeyId.BiometricVaultKek,
                cryptographicData = CryptographicData(
                    data = wrappedKey.key,
                    iv = wrappedKey.keyIV
                ),
                policy = BiometricPolicy(
                    negativeButton = BiometricString.NegativeButton.Password
                )
            ).bind {
                when (it) {
                    BiometricAuthError.Declined -> ChangePasswordError.BiometricDeclined
                    BiometricAuthError.Canceled -> ChangePasswordError.BiometricCanceled
                    else -> ChangePasswordError.BiometricAuthFailed
                }
            }.encoded

            val matches = session.verifyArk(unwrappedArk)
                .bind { ChangePasswordError.ActiveAccountNotFound }

            matches.asResult(ChangePasswordError.BiometricAuthFailed).bind()
        } finally {
            unwrappedArk?.fill(0)
        }
    }
}
