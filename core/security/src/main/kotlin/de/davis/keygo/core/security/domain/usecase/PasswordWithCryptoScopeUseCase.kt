package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class PasswordWithCryptoScopeUseCase(
    private val vaultRepository: VaultRepository,
    private val passwordRepository: PasswordRepository,
    private val cryptoScopeProvider: CryptographicScopeProvider,
) {

    suspend fun <R> observe(
        itemId: ItemId,
        block: suspend CryptographicScope.(Password) -> R
    ): Flow<R?> = passwordRepository.observePasswordById(itemId).map { password ->
        password?.let { handleItem(it, block) }
    }

    suspend fun <R> oneShot(
        itemId: ItemId,
        block: suspend CryptographicScope.(Password) -> R,
    ): R? {
        val password = passwordRepository.getPasswordById(itemId) ?: return null
        return handleItem(password, block)
    }

    private suspend fun <I : Item, R> handleItem(
        item: I,
        block: suspend CryptographicScope.(I) -> R,
    ): R? {
        val vaultKeyInfo = vaultRepository.getKeyInformation(item.vaultId) ?: return null

        return cryptoScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInfo,
                vaultId = item.vaultId,
            ),
            wrappedItemKeyInformation = item.wrappedItemKeyInformation()
        ) {
            block(item)
        }
    }
}
