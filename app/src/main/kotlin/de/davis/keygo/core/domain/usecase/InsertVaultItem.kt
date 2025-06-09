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
        val id = vaultItemRepository.createNewVaultItem(item)
        when (item) {
            is Password -> {
                passwordRepository.createNewPassword(item.copy(vaultItemId = id))
            }
        }
    }
}