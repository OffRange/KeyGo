package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

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

    private val _sessionStarts = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val sessionStarts: Flow<Unit> = _sessionStarts

    init {
        if (startOnConstruct) {
            _ark = ByteArray(32) { it.toByte() }
            _sessionStarts.tryEmit(Unit)
        }
    }

    override fun startSession(ark: ByteArray) {
        _ark = ark
        startSessionCalled = true
        _sessionStarts.tryEmit(Unit)
    }

    override fun endSession() {
        _ark = null
    }
}