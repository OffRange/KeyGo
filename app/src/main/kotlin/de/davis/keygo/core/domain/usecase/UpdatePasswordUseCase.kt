package de.davis.keygo.core.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.UpdateError
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.repository.VaultItemRepository
import org.koin.core.annotation.Single

@Single
class UpdatePasswordUseCase(
    private val vaultRepository: VaultItemRepository,
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider
) {

    suspend operator fun invoke(
        itemId: ItemId,
        name: String? = null,
        password: String? = null,
        username: String? = null,
        website: String? = null,
        note: String? = null
    ): Result<Unit, UpdateError> {
        if (name != null || note != null) {
            val vaultItem = vaultRepository.getVaultItem(itemId)
                ?: return Result.Failure(UpdateError.VaultItemNotFound)

            val updated = vaultItem.copy(
                name = name ?: vaultItem.name,
                note = note ?: vaultItem.note
            )

            vaultRepository.createNewOrUpdateVaultItem(updated)
        }

        if (password != null || username != null || website != null) {
            val passwordItem = passwordRepository.getVaultPasswordById(itemId)
                ?: return Result.Failure(UpdateError.VaultItemNotFound)

            val updatedPassword = passwordItem.copy(
                encryptedData = cryptographicScopeProvider.scope {
                    password?.encodeToByteArray()?.encrypt()
                } ?: passwordItem.encryptedData,
                username = username ?: passwordItem.username,
                website = website ?: passwordItem.website
            )

            passwordRepository.createNewOrUpdatePassword(updatedPassword)
        }

        return Result.Success(Unit)
    }
}