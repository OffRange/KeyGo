package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.vault.domain.model.VaultCreationError
import de.davis.keygo.rust.vault.VaultManager
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.wrapVaultKeyWithResult
import org.koin.core.annotation.Single

/**
 * This use case creates a new vault. This new vault is being used to update
 * [de.davis.keygo.core.item.domain.model.VaultContext] and last interacted vault.
 */
@Single
class CreateVaultUseCase(
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val vaultManager: VaultManager,
    private val keyWrapper: KeyWrapper,
    private val session: Session
) {

    suspend operator fun invoke(
        name: String,
        icon: Vault.Icon,
    ): Result<VaultId, VaultCreationError> {
        if (name.isBlank()) return Result.Failure(VaultCreationError.BlankName)

        val vaultId = newVaultId()

        val vaultKey = vaultManager.createNewVaultKey()
        val wrappedVaultKey =
            keyWrapper.wrapVaultKeyWithResult(session.dek.key.encoded, vaultKey, vaultId)
                .getOrNull() ?: return Result.Failure(VaultCreationError.WrapFailed)

        val vault = Vault(
            id = vaultId,
            name = name.trim(),
            icon = icon,
            wrappedVaultKey = wrappedVaultKey.ciphertext,
            vaultKeyNonce = wrappedVaultKey.nonce,
        )

        vaultRepository.createVault(vault)
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        return Result.Success(vaultId)
    }
}
