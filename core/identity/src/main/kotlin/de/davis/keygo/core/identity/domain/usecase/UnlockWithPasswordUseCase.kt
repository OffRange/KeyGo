package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
class UnlockWithPasswordUseCase(
    private val session: Session,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(password: String): Result<Unit, UnlockError> = resultBinding {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(UnlockError.ActiveAccountNotFound)

        val wrappedKey = account.passwordWrappedArk
        session.unlockWithPassword(
            password = password,
            salt = wrappedKey.salt,
            wrapped = WrappedKeyBlob(ciphertext = wrappedKey.key, nonce = wrappedKey.keyIV),
            userId = account.id,
        ).bind {
            if (it is SessionError.Derivation) UnlockError.DerivationFailed
            else UnlockError.UnwrappingFailed
        }
    }
}
