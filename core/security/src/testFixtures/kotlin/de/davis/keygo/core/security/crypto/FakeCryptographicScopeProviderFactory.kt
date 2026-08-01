package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProviderFactory

class FakeCryptographicScopeProviderFactory(
    private val provider: CryptographicScopeProvider,
) : CryptographicScopeProviderFactory {

    var lastSession: Session? = null
        private set

    override fun forSession(session: Session): CryptographicScopeProvider {
        lastSession = session
        return provider
    }
}
