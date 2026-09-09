package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.LegacySession
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProviderFactory

class FakeCryptographicScopeProviderFactory(
    private val provider: CryptographicScopeProvider,
) : CryptographicScopeProviderFactory {

    var lastSession: LegacySession? = null
        private set

    override fun forSession(session: LegacySession): CryptographicScopeProvider {
        lastSession = session
        return provider
    }
}
