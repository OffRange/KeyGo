package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import org.koin.core.annotation.Single

// TODO: implement actual move logic
@Single
class MoveItemsUseCase {

    suspend operator fun invoke(srcVaultId: VaultId, dstVaultId: VaultId) {
        // not implemented yet
    }
}
