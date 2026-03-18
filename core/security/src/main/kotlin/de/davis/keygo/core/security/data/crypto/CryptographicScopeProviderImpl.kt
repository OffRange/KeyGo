package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    private val session: Session
) : CryptographicScopeProvider {

    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        CryptographicScopeImpl(session.dek).block()
}