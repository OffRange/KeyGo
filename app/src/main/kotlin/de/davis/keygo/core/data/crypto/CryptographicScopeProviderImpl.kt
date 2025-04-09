package de.davis.keygo.core.data.crypto

import de.davis.keygo.core.domain.crypto.CryptographicScope
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.crypto.EncryptionKeyProvider

class CryptographicScopeProviderImpl(
    private val aesKeyProvider: EncryptionKeyProvider
) : CryptographicScopeProvider {

    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        with(aesKeyProvider.provide()) {
            CryptographicScopeImpl(this).block()
        }
}