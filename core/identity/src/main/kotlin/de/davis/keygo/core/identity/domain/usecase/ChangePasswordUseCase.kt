package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.rust.derive.KeyDeriver
import de.davis.keygo.rust.derive.deriveRootKekFromPasswordWithResult
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.unwrapAccountRootKeyWithResult
import de.davis.keygo.rust.wrap.wrapAccountRootKeyWithResult
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
class ChangePasswordUseCase(
    private val accountRepository: AccountRepository,
    private val keyDeriver: KeyDeriver,
    private val keyWrapper: KeyWrapper,
) {

    suspend operator fun invoke(
        reauthentication: Reauthentication,
        newPassword: String,
    ): Result<Unit, ChangePasswordError> = resultBinding {
        val account = accountRepository.getOrNull()
            ?: return Result.Failure(ChangePasswordError.ActiveAccountNotFound)

        val ark = when (reauthentication) {
            is Reauthentication.Password -> {
                val kek = keyDeriver.deriveRootKekFromPasswordWithResult(
                    password = reauthentication.currentPassword,
                    salt = account.passwordWrappedArk.salt,
                ).bind { ChangePasswordError.KeyDerivationFailed }

                keyWrapper.unwrapAccountRootKeyWithResult(
                    kek = kek,
                    wrapped = WrappedKeyBlob(
                        ciphertext = account.passwordWrappedArk.key,
                        nonce = account.passwordWrappedArk.keyIV,
                    ),
                    userId = account.id,
                ).bind { ChangePasswordError.IncorrectPassword }
            }

            is Reauthentication.Biometric -> {
                account.biometricWrappedArk
                    ?: return Result.Failure(ChangePasswordError.BiometricNotEnrolled)
                reauthentication.recoveredArk
            }
        }

        val newSalt = keyDeriver.generateSalt()
        val newKek = keyDeriver.deriveRootKekFromPasswordWithResult(
            password = newPassword,
            salt = newSalt,
        ).bind { ChangePasswordError.KeyDerivationFailed }

        val rewrapped = keyWrapper.wrapAccountRootKeyWithResult(
            kek = newKek,
            ark = ark,
            userId = account.id,
        ).bind { ChangePasswordError.WrappingFailed }

        accountRepository.set(
            account.copy(
                passwordWrappedArk = PasswordWrappedArk(
                    key = rewrapped.ciphertext,
                    keyIV = rewrapped.nonce,
                    salt = newSalt,
                ),
            ),
        ).bind { ChangePasswordError.PersistenceFailed }

        ark.fill(0)
    }
}
