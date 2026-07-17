package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProviderFactory
import de.davis.keygo.rust.item.ItemManager
import de.davis.keygo.rust.wrap.KeyWrapper
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderFactoryImpl(
    private val itemRepository: ItemRepository,
    private val itemManager: ItemManager,
    private val keyWrapper: KeyWrapper,
) : CryptographicScopeProviderFactory {

    override fun forSession(session: Session): CryptographicScopeProvider =
        CryptographicScopeProviderImpl(session, itemRepository, itemManager, keyWrapper)
}
