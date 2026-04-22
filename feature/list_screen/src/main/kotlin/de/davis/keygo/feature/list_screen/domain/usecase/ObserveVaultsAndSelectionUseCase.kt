package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.feature.list_screen.domain.model.VaultsAndSelection
import de.davis.keygo.feature.list_screen.domain.repository.SelectedVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single

@Single
class ObserveVaultsAndSelectionUseCase(
    private val vaultRepository: VaultRepository,
    private val selectedVaultRepository: SelectedVaultRepository,
) {
    operator fun invoke(): Flow<VaultsAndSelection> = combine(
        vaultRepository.observeAllVaultMetadata(),
        selectedVaultRepository.observe(),
    ) { vaults, selection ->
        VaultsAndSelection(vaults = vaults, selection = selection)
    }
}
