package de.davis.keygo.core.data.crypto

import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.crypto.CryptographicScope
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    private val session: Session
) : CryptographicScopeProvider {

    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        CryptographicScopeImpl(session.scope.get()).block()
}