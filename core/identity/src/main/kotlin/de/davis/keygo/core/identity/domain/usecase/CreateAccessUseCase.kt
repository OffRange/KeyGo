package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.asAesKey
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.account.AccountManager
import de.davis.keygo.rust.derive.KeyDeriver
import de.davis.keygo.rust.derive.deriveRootKekFromPasswordWithResult
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.wrapAccountRootKeyWithResult
import de.davis.keygo.rust.wrap.wrapVaultKeyWithResult
import de.davisalessandro.keygo.rust.AccountRootKey
import de.davisalessandro.keygo.rust.RootKek
import org.koin.core.annotation.Single
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import de.davisalessandro.keygo.rust.Account as RustAccount


@Single
class CreateAccessUseCase(
    private val keyDeriver: KeyDeriver,
    private val keyWrapper: KeyWrapper,
    private val accountManager: AccountManager,
    private val accountRepository: AccountRepository,
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val session: Session
) {

    /**
     * Use case to create access by generating a new account and vault, which are then wrapped
     * with a Key Encryption Key (KEK) derived from the user's password. Optionally, the ARK
     * (AccountRootKey) can also be wrapped with a KEK derived from biometric data.
     *
     * The generated ARK is stored in the session for immediate use. The password-wrapped ARK and,
     * if applicable, the biometric-wrapped ARK are stored in the [AccountRepository] for future
     * retrieval.
     *
     * @param password The user's password used to derive the KEK for wrapping the ARK.
     * @param biometricCipher An optional [Cipher] initialized for wrapping the ARK with biometric data.
     */
    suspend operator fun invoke(
        password: String,
        biometricCipher: Cipher? = null,
        vaultName: String = "Default Vault",
        accountDisplayName: String = "Default Account",
    ): Result<Unit, CreateAccessError> {
        val salt = keyDeriver.generateSalt()
        val derivedKek = keyDeriver.deriveRootKekFromPasswordWithResult(
            password = password,
            salt = salt,
        ).getOrNull() ?: return Result.Failure(CreateAccessError.KeyDerivationFailed)

        val accountHolder = accountManager.createAccount()

        val passwordWrappedArk =
            getPasswordWrappedArk(accountHolder.account, derivedKek, salt).getOrNull()
                ?: return Result.Failure(CreateAccessError.WrappingFailed)

        accountHolder.defaultVault.wrap(accountHolder.account.ark).getOrNull()?.let { wrappedKey ->
            // TODO: use CreateVaultUseCase, when having better project structure
            vaultRepository.createVault(
                Vault(
                    id = accountHolder.defaultVault.id,
                    name = vaultName,
                    wrappedVaultKey = wrappedKey.ciphertext,
                    vaultKeyNonce = wrappedKey.nonce,
                    icon = Vault.Icon.Default
                )
            )
            vaultContextRepository.setContextAndLastInteracted(accountHolder.defaultVault.id)
        } ?: return Result.Failure(CreateAccessError.WrappingFailed)

        val biometricWrappedArk = biometricCipher?.let {
            getBiometricWrappedArk(accountHolder.account, biometricCipher).getOrNull()
                ?: return Result.Failure(CreateAccessError.WrappingFailed)
        }

        accountRepository.set(
            Account(
                id = accountHolder.account.id,
                displayName = accountDisplayName,
                passwordWrappedArk = passwordWrappedArk,
                biometricWrappedArk = biometricWrappedArk,
            )
        ).getOrNull() ?: return Result.Failure(CreateAccessError.AccountPersistenceFailed)

        session.startSession(accountHolder.account.ark.asAesKey())
        return Result.Success(Unit)
    }

    private fun getPasswordWrappedArk(
        account: RustAccount,
        derivedKek: RootKek,
        salt: ByteArray
    ) = account.wrap(derivedKek)
        .getOrNull()
        ?.let { wrappedKey ->
            PasswordWrappedArk(
                key = wrappedKey.ciphertext,
                keyIV = wrappedKey.nonce,
                salt = salt
            )
        }.asResult(CreateAccessError.WrappingFailed)

    private fun getBiometricWrappedArk(
        account: RustAccount,
        biometricCipher: Cipher
    ) = account.wrapUsingCipher(biometricCipher)
        ?.let { (wrappedKey, iv) ->
            BiometricWrappedArk(
                key = wrappedKey,
                keyIV = iv
            )
        }.asResult(CreateAccessError.WrappingFailed)

    private fun de.davisalessandro.keygo.rust.Vault.wrap(ark: AccountRootKey) =
        keyWrapper.wrapVaultKeyWithResult(ark, vaultKey, id)

    private fun RustAccount.wrap(kek: RootKek) =
        keyWrapper.wrapAccountRootKeyWithResult(kek, ark, id)

    private fun RustAccount.wrapUsingCipher(cipher: Cipher) = runCatching {
        cipher.wrap(SecretKeySpec(ark, 0, ark.size, "AES")) to cipher.iv
    }.getOrNull()
}
