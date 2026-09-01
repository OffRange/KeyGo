package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A fake implementation of [Session] that provides a fixed DEK for testing purposes.
 */
class FakeSession(
    private val startOnConstruct: Boolean = false
) : Session {

    var startSessionCalled = false

    private var _ark: ByteArray? = null
    private val _isActive = MutableStateFlow(false)

    override val ark: ByteArray?
        get() = _ark

    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    init {
        if (startOnConstruct) {
            _ark = ByteArray(32) { it.toByte() }
            _isActive.value = true
        }
    }

    override fun startSession(ark: ByteArray) {
        _ark = ark
        _isActive.value = true
        startSessionCalled = true
    }

    override fun endSession() {
        _ark = null
        _isActive.value = false
    }
}