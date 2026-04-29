package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultUpdater
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.list_screen.domain.model.VaultCreationError
import org.koin.core.annotation.Single

// TODO: write tests
@Single
class EditVaultUseCase(
    private val vaultRepository: VaultRepository
) {

    suspend operator fun invoke(
        vaultId: VaultId,
        name: String,
        icon: Vault.Icon
    ): Result<Unit, VaultCreationError> {
        if (name.isBlank()) return Result.Failure(VaultCreationError.BlankName)

        vaultRepository.updateVault(
            VaultUpdater(
                id = vaultId,
                name = name.trim(),
                icon = icon,
            )
        )

        return Result.Success(Unit)
    }
}