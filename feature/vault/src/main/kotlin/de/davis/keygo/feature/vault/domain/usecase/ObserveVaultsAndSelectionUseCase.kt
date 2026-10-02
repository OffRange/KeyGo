package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.util.domain.comparator.sortedNaturallyBy
import de.davis.keygo.feature.vault.domain.model.VaultsAndSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single

@Single
class ObserveVaultsAndSelectionUseCase(
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
) {
    operator fun invoke(): Flow<VaultsAndSelection> = combine(
        vaultRepository.observeAllVaultMetadata(),
        vaultContextRepository.observeVaultContext(),
    ) { vaults, context ->
        VaultsAndSelection(
            vaults = vaults.sortedNaturallyBy { it.name },
            selection = context,
        )
    }
}
