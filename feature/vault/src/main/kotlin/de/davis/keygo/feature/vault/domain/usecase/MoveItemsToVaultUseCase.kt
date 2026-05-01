package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.vault.domain.model.MoveItemsError
import de.davisalessandro.keygo.rust.ItemAad
import org.koin.core.annotation.Single

@Single
class MoveItemsToVaultUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val itemRepository: ItemRepository,
    private val vaultRepository: VaultRepository,
) {

    suspend operator fun invoke(
        srcVaultId: VaultId,
        dstVaultId: VaultId,
    ): Result<Unit, MoveItemsError> {
        if (srcVaultId == dstVaultId) return Result.Success(Unit)

        val srcVault = WrappedVaultKeyInformation(
            wrappedVaultKey = vaultRepository.getKeyInformation(srcVaultId)
                ?: return Result.Failure(MoveItemsError.VaultNotFound(srcVaultId)),
            vaultId = srcVaultId,
        )
        val dstVault = WrappedVaultKeyInformation(
            wrappedVaultKey = vaultRepository.getKeyInformation(dstVaultId)
                ?: return Result.Failure(MoveItemsError.VaultNotFound(dstVaultId)),
            vaultId = dstVaultId,
        )

        itemRepository.getMovableItemsByVault(srcVaultId).forEach { item ->
            val rewrapped = runCatching {
                cryptographicScopeProvider.rewrapItemKey(
                    sourceVault = srcVault,
                    sourceItem = WrappedItemKeyInformation(
                        itemAad = ItemAad(itemId = item.id, vaultId = srcVaultId),
                        wrappedItemKey = item.keyInformation,
                    ),
                    destinationVault = dstVault,
                )
            }.getOrElse {
                return Result.Failure(MoveItemsError.ItemMoveFailed(item.id, it))
            }

            itemRepository.moveItem(
                itemId = item.id,
                newVaultId = dstVaultId,
                newKeyInformation = rewrapped,
            ).onFailure {
                return Result.Failure(MoveItemsError.ItemMoveFailed(item.id, it))
            }
        }
        return Result.Success(Unit)
    }
}
