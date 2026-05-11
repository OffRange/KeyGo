package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.derive.KeyDeriver
import de.davis.keygo.rust.derive.deriveRootKekFromPasswordWithResult
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.unwrapAccountRootKeyWithResult
import de.davisalessandro.keygo.rust.AccountRootKey
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.RootKek
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class UnlockWithPasswordUseCase(
    private val session: Session,
    private val accountRepository: AccountRepository,
    private val keyDeriver: KeyDeriver,
    private val keyWrapper: KeyWrapper,
) {

    suspend operator fun invoke(password: String): Result<Unit, UnlockError> {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(UnlockError.ActiveAccountNotFound)

        val wrappedKey = account.passwordWrappedArk
        val derivedKey = keyDeriver.deriveRootKekFromPasswordWithResult(
            password = password,
            salt = wrappedKey.salt,
        ).getOrNull() ?: return Result.Failure(UnlockError.DerivationFailed)

        val key = wrappedKey.unwrapUsing(derivedKey, account.id)
            .getOrNull()
            ?: return Result.Failure(UnlockError.UnwrappingFailed)

        session.startSession(key)
        return Result.Success(Unit)
    }

    private fun PasswordWrappedArk.unwrapUsing(
        kek: RootKek,
        userId: UUID,
    ): Result<AccountRootKey, KeyWrapException> = keyWrapper.unwrapAccountRootKeyWithResult(
        kek = kek,
        wrapped = WrappedKeyBlob(ciphertext = this.key, nonce = this.keyIV),
        userId = userId,
    )
}
