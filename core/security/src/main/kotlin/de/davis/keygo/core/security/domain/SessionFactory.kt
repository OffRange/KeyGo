package de.davis.keygo.core.security.domain

/**
 * Builds sessions that are not the app-wide one. Backup uses this to run against an ARK recovered
 * from escrow without touching global state. It is an interface so tests can supply a session over
 * [de.davis.keygo.rust.FakeArkSession]: the real UniFFI class needs the native library.
 */
fun interface SessionFactory {
    fun create(): Session
}
