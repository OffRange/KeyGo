package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class LoginWithCryptoScopeUseCase(
    private val vaultRepository: VaultRepository,
    private val loginRepository: LoginRepository,
    private val cryptoScopeProvider: CryptographicScopeProvider,
) {

    suspend fun <R> observe(
        itemId: ItemId,
        block: suspend CryptographicScope.(Login) -> R,
    ): Flow<R?> = loginRepository.observeLoginById(itemId).map { login ->
        login?.let { handleItem(it, block) }
    }

    suspend fun <R> oneShot(
        itemId: ItemId,
        block: suspend CryptographicScope.(Login) -> R,
    ): R? {
        val login = loginRepository.getLoginById(itemId) ?: return null
        return handleItem(login, block)
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
            wrappedItemKeyInformation = item.wrappedItemKeyInformation(),
        ) {
            block(item)
        }
    }
}
