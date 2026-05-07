package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Single

@Single
class UpsertVaultItemUseCase(
    private val passwordRepository: PasswordRepository,
) {

    suspend operator fun invoke(item: Item): Result<ItemId, Throwable> = when (item) {
        is Login -> {
            passwordRepository.createOrUpdatePassword(item)
        }
    }
}