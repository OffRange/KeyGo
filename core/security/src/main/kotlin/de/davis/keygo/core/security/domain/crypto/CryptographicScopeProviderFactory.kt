package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.security.domain.LegacySession

/**
 * Builds a [CryptographicScopeProvider] bound to a specific [LegacySession]. The default binding uses
 * the app-wide session; backup uses this to run against a recovered ARK without mutating global state.
 */
fun interface CryptographicScopeProviderFactory {
    fun forSession(session: LegacySession): CryptographicScopeProvider
}
