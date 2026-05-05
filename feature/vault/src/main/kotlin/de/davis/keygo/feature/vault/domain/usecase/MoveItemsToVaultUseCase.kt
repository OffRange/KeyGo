package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.MovableItem
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.vault.domain.model.MoveItemsError
import de.davis.keygo.feature.vault.domain.model.MoveItemsProgress
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
        onProgress: (MoveItemsProgress) -> Unit = {},
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

        val items = itemRepository.getMovableItemsByVault(srcVaultId)
        val total = items.size
        onProgress(MoveItemsProgress(movedCount = 0, total = total))

        // Phase 1: re-wrap every item key under the destination vault. Done before any DB write
        // so a per-item rewrap failure aborts cleanly with the source vault untouched.
        val rewrapped = ArrayList<MovableItem>(total)
        items.forEachIndexed { index, item ->
            val newKey = when (
                val r = cryptographicScopeProvider.rewrapItemKey(
                    sourceVault = srcVault,
                    sourceItem = WrappedItemKeyInformation(
                        itemAad = ItemAad(itemId = item.id, vaultId = srcVaultId),
                        wrappedItemKey = item.keyInformation,
                    ),
                    destinationVault = dstVault,
                )
            ) {
                is Result.Success -> r.success
                is Result.Failure -> return Result.Failure(
                    MoveItemsError.ItemMoveFailed(item.id, r.error),
                )
            }
            rewrapped += MovableItem(id = item.id, keyInformation = newKey)
            onProgress(MoveItemsProgress(movedCount = index + 1, total = total))
        }

        // Phase 2: commit all moves in one SQLite transaction. On failure, every row rolls
        // back — items remain in the source vault, decryptable under the source vault key.
        itemRepository.moveItemsToVault(rewrapped, dstVaultId).onFailure {
            return Result.Failure(MoveItemsError.PersistFailed(it))
        }

        return Result.Success(Unit)
    }
}
