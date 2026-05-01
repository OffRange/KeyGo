package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import org.koin.core.annotation.Single

/**
 * This use case sets the vault context and also updates last interacted Vault.
 */
@Single
class SetVaultContextUseCase(
    private val vaultContextRepository: VaultContextRepository
) {

    /**
     * Updates the vault context. Also updates last interacted vault when [context] is of
     * type [VaultContext.ById].
     */
    suspend operator fun invoke(context: VaultContext) {
        vaultContextRepository.setVaultContext(context)

        if (context is VaultContext.ById)
            vaultContextRepository.setLastInteractedVault(context.vaultId)
    }
}
