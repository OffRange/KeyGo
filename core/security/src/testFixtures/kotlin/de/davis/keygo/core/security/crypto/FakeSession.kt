package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.Session

/**
 * A fake implementation of [Session] that provides a fixed DEK for testing purposes.
 */
class FakeSession(
    private val startOnConstruct: Boolean = false
) : Session {

    var startSessionCalled = false

    private var _ark: ByteArray? = null
    override val ark: ByteArray
        get() = _ark ?: throw IllegalStateException("FakeSession not started")

    init {
        if (startOnConstruct) _ark = ByteArray(32) { it.toByte() }
    }

    override fun startSession(ark: ByteArray) {
        _ark = ark
        startSessionCalled = true
    }

    override fun endSession() {
        _ark = null
    }
}