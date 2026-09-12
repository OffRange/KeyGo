package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.vault.domain.model.VaultCreationError
import de.davis.keygo.rust.vault.VaultManager
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
    private val session: Session,
) {

    suspend operator fun invoke(
        name: String,
        icon: Vault.Icon,
    ): Result<VaultId, VaultCreationError> = resultBinding {
        if (name.isBlank()) return Result.Failure(VaultCreationError.BlankName)

        val vaultId = newVaultId()

        val vaultKey = vaultManager.createNewVaultKey()
        val wrappedVaultKey = session.wrapVaultKey(vaultKey, vaultId)
            .bind {
                if (it == SessionError.Locked) VaultCreationError.NoActiveSession
                else VaultCreationError.WrapFailed
            }

        val vault = Vault(
            id = vaultId,
            name = name.trim(),
            icon = icon,
            wrappedVaultKey = wrappedVaultKey.ciphertext,
            vaultKeyNonce = wrappedVaultKey.nonce,
        )

        vaultRepository.createVault(vault)
        vaultContextRepository.setContextAndLastInteracted(vaultId)
        vaultId
    }
}
