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
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import org.koin.core.annotation.Single
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Single
class CreateAccessUseCase(
    private val accountRepository: AccountRepository,
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val session: Session,
) {

    /**
     * Use case to create access by generating a new account and vault. The session mints the ARK
     * in Rust, wraps it under a KEK derived from the user's password, and keeps custody of it, so
     * the caller is left unlocked without the key ever reaching the JVM heap. Optionally, a second
     * copy of the ARK is wrapped with a biometric-backed Keystore cipher.
     *
     * The password-wrapped ARK and, if applicable, the biometric-wrapped ARK are stored in the
     * [AccountRepository] for future retrieval.
     *
     * @param password The user's password used to derive the KEK for wrapping the ARK.
     * @param biometricCipher An optional [Cipher] initialized for wrapping the ARK with biometric data.
     */
    suspend operator fun invoke(
        password: String,
        biometricCipher: Cipher? = null,
        vaultName: String = "Default Vault",
        accountDisplayName: String = "Default Account",
    ): Result<Unit, CreateAccessError> = resultBinding {
        val created = session.createAccount(password)
            .bind {
                if (it is SessionError.Derivation) CreateAccessError.KeyDerivationFailed
                else CreateAccessError.WrappingFailed
            }

        val biometricWrappedArk = biometricCipher?.let { cipher ->
            val ark = session.exportArk().bind { CreateAccessError.WrappingFailed }
            try {
                wrapArk(ark, cipher).asResult(CreateAccessError.WrappingFailed).bind()
            } finally {
                ark.fill(0)
            }
        }

        // Persist the account before the vault: the vault is encrypted under the account's
        // ARK, so a vault row without a recoverable account is dead weight. If the vault
        // write fails after this, the half-state is recoverable on retry, since `set` overwrites.
        accountRepository.set(
            Account(
                id = created.userId,
                displayName = accountDisplayName,
                passwordWrappedArk = PasswordWrappedArk(
                    key = created.passwordWrappedArk.ciphertext,
                    keyIV = created.passwordWrappedArk.nonce,
                    salt = created.salt,
                ),
                biometricWrappedArk = biometricWrappedArk,
            )
        ).bind { CreateAccessError.AccountPersistenceFailed }

        // TODO: use CreateVaultUseCase, when having better project structure
        runCatching {
            vaultRepository.createVault(
                Vault(
                    id = created.vaultId,
                    name = vaultName,
                    wrappedVaultKey = created.wrappedVaultKey.ciphertext,
                    vaultKeyNonce = created.wrappedVaultKey.nonce,
                    icon = Vault.Icon.Default,
                )
            )
        }.onFailure { return Result.Failure(CreateAccessError.VaultPersistenceFailed(it)) }

        vaultContextRepository.setContextAndLastInteracted(created.vaultId)
    }

    private fun wrapArk(ark: ByteArray, cipher: Cipher): BiometricWrappedArk? = runCatching {
        BiometricWrappedArk(
            key = cipher.wrap(SecretKeySpec(ark, 0, ark.size, "AES")),
            keyIV = cipher.iv,
        )
    }.getOrNull()
}
