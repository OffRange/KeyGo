package de.davis.keygo.core.domain.usecase

import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.repository.VaultItemRepository
import org.koin.core.annotation.Single

@Single
class InsertVaultItem(
    private val vaultItemRepository: VaultItemRepository,
    private val passwordRepository: PasswordRepository,
) {

    suspend operator fun <I : VaultItem> invoke(item: I) {
        val id = vaultItemRepository.createNewOrUpdateVaultItem(item)
        when (item) {
            is Password -> {
                passwordRepository.createNewOrUpdatePassword(item.copy(vaultItemId = id))
            }

            else -> {}
        }
    }
}