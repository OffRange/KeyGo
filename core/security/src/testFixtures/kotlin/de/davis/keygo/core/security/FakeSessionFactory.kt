package de.davis.keygo.core.security

import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionFactory

/** Hands out a fresh, locked [FakeSession] per call and keeps each one for inspection. */
class FakeSessionFactory : SessionFactory {

    /** Applied to every session created from here on. */
    var failUnlock: Boolean = false

    val created: MutableList<FakeSession> = mutableListOf()

    override fun create(): Session =
        FakeSession().also {
            it.failUnlock = failUnlock
            created += it
        }
}
