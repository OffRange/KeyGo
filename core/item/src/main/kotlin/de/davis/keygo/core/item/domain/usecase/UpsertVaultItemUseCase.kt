package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Single

@Single
class UpsertVaultItemUseCase(
    private val passwordRepository: PasswordRepository,
    private val vaultItemRepository: VaultItemRepository
) {

    suspend operator fun invoke(item: VaultItem): Result<Unit, Throwable> = runCatching {
        val id = vaultItemRepository.createOrUpdateVaultItem(item)
            .takeIf { it != -1L }
            ?: item.vaultItemId

        when (item) {
            is Password -> {
                passwordRepository.createOrUpdatePassword(item.copy(vaultItemId = id))
            }

            else -> {}
        }
    }.fold(
        onFailure = { Result.Failure(it) },
        onSuccess = { Result.Success(Unit) }
    )
}