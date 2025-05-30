package de.davis.keygo.core.domain

import de.davis.keygo.core.domain.model.crypto.AesKey
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.component.getScopeOrNull
import org.koin.core.scope.Scope

interface Session : KoinScopeComponent {

    override val scope: Scope
        get() = getScopeOrNull() ?: createScope()

    fun startSession(dek: AesKey)
    fun endSession() // TODO invoke on logout / app close
}