package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.VaultItem
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asUnitResult
import org.koin.core.annotation.Single

@Single
class UpsertVaultItemUseCase(
    private val passwordRepository: PasswordRepository,
) {

    suspend operator fun invoke(item: VaultItem): Result<Unit, Throwable> = when (item) {
        is Password -> {
            passwordRepository.createOrUpdatePassword(item)
        }
    }.asUnitResult()
}