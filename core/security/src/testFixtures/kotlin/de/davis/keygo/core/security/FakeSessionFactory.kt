package de.davis.keygo.core.security

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionFactory

class FakeSessionFactory : SessionFactory {

    var failUnlock: Boolean = false

    val created: MutableList<FakeSession> = mutableListOf()

    override fun create(): Session =
        FakeSession().also {
            it.failUnlock = failUnlock
            created += it
        }
}
