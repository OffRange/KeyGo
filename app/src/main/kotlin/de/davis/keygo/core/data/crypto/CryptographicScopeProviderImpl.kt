package de.davis.keygo.core.data.crypto

import de.davis.keygo.core.domain.crypto.CryptographicScope
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.crypto.AesKey

internal class CryptographicScopeProviderImpl(
    private val aesKey: AesKey
) : CryptographicScopeProvider {

    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        CryptographicScopeImpl(aesKey).block()
}