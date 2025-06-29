package de.davis.keygo.core.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.repository.VaultItemRepository
import org.koin.core.annotation.Single

@Single
class UpsertVaultItem(
    private val vaultItemRepository: VaultItemRepository,
    private val passwordRepository: PasswordRepository,
) {

    suspend operator fun <I : VaultItem> invoke(item: I): Result<Unit, Throwable> = runCatching {
        val id = vaultItemRepository.createNewOrUpdateVaultItem(item)
            .takeIf { it != -1L }
            ?: item.vaultItemId
        
        when (item) {
            is Password -> {
                passwordRepository.createNewOrUpdatePassword(item.copy(vaultItemId = id))
            }

            else -> {}
        }
    }.fold(
        onFailure = { Result.Failure(it) },
        onSuccess = { Result.Success(Unit) }
    )
}