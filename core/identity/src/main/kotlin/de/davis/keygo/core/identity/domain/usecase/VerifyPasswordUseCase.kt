package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.VerifyPasswordError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
class VerifyPasswordUseCase(
    private val session: Session,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(password: String): Result<Unit, VerifyPasswordError> {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(VerifyPasswordError.ActiveAccountNotFound)

        return session.verifyPassword(
            password = password,
            salt = account.passwordWrappedArk.salt,
            wrapped = WrappedKeyBlob(
                ciphertext = account.passwordWrappedArk.key,
                nonce = account.passwordWrappedArk.keyIV,
            ),
            userId = account.id,
        ).mapFailure {
            when (it) {
                is SessionError.Derivation -> VerifyPasswordError.KeyDerivationFailed
                SessionError.Locked -> VerifyPasswordError.ActiveAccountNotFound
                else -> VerifyPasswordError.IncorrectPassword
            }
        }
    }
}
