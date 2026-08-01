package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.security.domain.Session

/**
 * Builds a [CryptographicScopeProvider] bound to a specific [Session]. The default binding uses the
 * app-wide session; backup uses this to run against a recovered ARK without mutating global state.
 */
fun interface CryptographicScopeProviderFactory {
    fun forSession(session: Session): CryptographicScopeProvider
}
