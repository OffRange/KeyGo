package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class ItemWithCryptoScopeUseCase(
    private val vaultRepository: VaultRepository,
    private val cryptoScopeProvider: CryptographicScopeProvider,
) {

    suspend fun <I : Item, R> oneShot(
        itemId: ItemId,
        fetch: suspend (ItemId) -> I?,
        block: suspend CryptographicScope.(I) -> R,
    ): Result<R, CryptoScopeError> {
        val item = fetch(itemId)
            ?: return Result.Failure(CryptoScopeError.IdNotFound)
        return withItem(item, block)
    }

    suspend fun <I : Item, R> observe(
        itemId: ItemId,
        source: (ItemId) -> Flow<I?>,
        block: suspend CryptographicScope.(I) -> R,
    ): Flow<Result<R, CryptoScopeError>> = source(itemId).map { item ->
        item?.let { withItem(it, block) }
            ?: Result.Failure(CryptoScopeError.IdNotFound)
    }

    suspend fun <I : Item, R> withItem(
        item: I,
        block: suspend CryptographicScope.(I) -> R,
    ): Result<R, CryptoScopeError> {
        val vaultKeyInfo =
            vaultRepository.getKeyInformation(item.vaultId)
                ?: return Result.Failure(CryptoScopeError.IdNotFound)

        return cryptoScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInfo,
                vaultId = item.vaultId,
            ),
            wrappedItemKeyInformation = item.wrappedItemKeyInformation(),
        ) {
            block(item)
        }
    }
}
