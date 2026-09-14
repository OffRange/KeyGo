package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.core.util.resultBinding
import org.koin.core.annotation.Single

@Single
class CreateAccessUseCase(
    private val accountRepository: AccountRepository,
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val enableBiometrics: EnableBiometricsUseCase,
    private val session: Session,
) {

    suspend operator fun invoke(
        password: String,
        withBiometrics: Boolean = false,
        vaultName: String = "Default Vault",
        accountDisplayName: String = "Default Account",
        policy: BiometricPolicy = BiometricPolicy.Default,
    ): Result<Unit, CreateAccessError> {
        var handBack = true
        try {
            val result = create(password, vaultName, accountDisplayName)
            handBack = result.isFailure()
            if (result.isSuccess() && withBiometrics) enableBiometrics(policy)
            return result
        } finally {
            if (handBack) session.endSession()
        }
    }

    private suspend fun create(
        password: String,
        vaultName: String,
        accountDisplayName: String,
    ): Result<Unit, CreateAccessError> = resultBinding {
        val created = session.createAccount(password)
            .bind {
                if (it is SessionError.Derivation) CreateAccessError.KeyDerivationFailed
                else CreateAccessError.WrappingFailed
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
                biometricWrappedArk = null,
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
}
