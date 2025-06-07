package de.davis.keygo.core.data.crypto

import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.crypto.CryptographicScope
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.crypto.AesKey
import org.koin.core.annotation.ScopeId
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    @ScopeId(name = Session.SCOPE_ID)
    private val aesKey: AesKey
) : CryptographicScopeProvider {

    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        CryptographicScopeImpl(aesKey).block()
}