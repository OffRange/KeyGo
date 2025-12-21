package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.crypto.model.AesKey
import org.koin.core.component.KoinScopeComponent
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

interface Session : KoinScopeComponent {

    override val scope: Scope
        get() = getKoin().getScopeOrNull(SCOPE_ID) ?: getKoin().createScope(
            SCOPE_ID,
            named<Session>()
        )

    fun startSession(dek: AesKey)
    fun endSession() // TODO invoke on logout / app close

    companion object {
        const val SCOPE_ID = "session-scope"
    }
}