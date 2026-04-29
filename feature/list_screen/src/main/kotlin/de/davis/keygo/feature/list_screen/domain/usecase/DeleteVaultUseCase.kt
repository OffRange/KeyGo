package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.getIdOrNull
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import org.koin.core.annotation.Single

@Single
class DeleteVaultUseCase(
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
) {

    suspend operator fun invoke(vaultId: VaultId) {
        val record = vaultContextRepository.getVaultContextRecord()

        if (record.context.getIdOrNull() == vaultId)
            vaultContextRepository.setVaultContext(VaultContext.NoSpecific)

        if (record.lastInteractedVaultId == vaultId) {
            vaultRepository.getLastCreatedVaultId(vaultId)?.let { lastCreated ->
                vaultContextRepository.setLastInteractedVault(lastCreated)
            }
        }

        vaultRepository.deleteVault(vaultId)
    }
}
