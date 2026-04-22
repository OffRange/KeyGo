package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.domain.repository.SelectedVaultRepository
import org.koin.core.annotation.Single

@Single
class SelectVaultUseCase(
    private val vaultRepository: VaultRepository,
    private val selectedVaultRepository: SelectedVaultRepository,
) {
    suspend operator fun invoke(selection: SelectedVault) {
        selectedVaultRepository.set(selection)
        if (selection is SelectedVault.Id) vaultRepository.setActiveVault(selection.vaultId)
    }
}
