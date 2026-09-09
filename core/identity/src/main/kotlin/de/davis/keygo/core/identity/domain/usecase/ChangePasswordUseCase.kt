package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

/**
 * Changes the account password by re-wrapping the ARK the session already holds. It therefore
 * needs an active session, which the change-password screen guarantees: it is only reachable from
 * inside an unlocked app, and it clears itself the moment the session ends.
 */
@Single
class ChangePasswordUseCase(
    private val accountRepository: AccountRepository,
    private val session: Session,
) {

    suspend operator fun invoke(
        reauthentication: Reauthentication,
        newPassword: String,
    ): Result<Unit, ChangePasswordError> = try {
        changePassword(reauthentication, newPassword)
    } finally {
        // We take ownership of the caller-supplied ARK: never leave a copy behind,
        // even when an early validation bails out or re-wrapping fails part-way.
        if (reauthentication is Reauthentication.Biometric) reauthentication.recoveredArk.fill(0)
    }

    private suspend fun changePassword(
        reauthentication: Reauthentication,
        newPassword: String,
    ): Result<Unit, ChangePasswordError> = resultBinding {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(ChangePasswordError.ActiveAccountNotFound)

        when (reauthentication) {
            is Reauthentication.Password -> session.verifyPassword(
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

            is Reauthentication.Biometric -> {
                account.biometricWrappedArk
                    ?: return Result.Failure(ChangePasswordError.BiometricNotEnrolled)
                if (!session.verifyArk(reauthentication.recoveredArk))
                    return Result.Failure(ChangePasswordError.IncorrectPassword)
            }
        }

        val rewrapped = session.rewrapForNewPassword(newPassword, account.id).bind {
            if (it is SessionError.Derivation) ChangePasswordError.KeyDerivationFailed
            else ChangePasswordError.WrappingFailed
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
}
