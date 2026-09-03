package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.ArkHolder
import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A fake [Session] with a fixed ARK. Shares [ArkHolder] with the real one, so it wipes the same
 * way - a fake that skipped the wipe would hide use-after-wipe bugs from every test.
 */
class FakeSession(
    startOnConstruct: Boolean = false
) : Session {

    var startSessionCalled = false

    private val holder = ArkHolder()
    private var live: ByteArray? = null
    private val _isActive = MutableStateFlow(false)

    /** The live ARK as a copy, for assertions. Null once the session has ended. */
    val currentArk: ByteArray?
        get() = live?.copyOf()

    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    init {
        // Constructing pre-unlocked is not a startSession call.
        if (startOnConstruct) {
            startSession(ByteArray(32) { it.toByte() })
            startSessionCalled = false
        }
    }

    override suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? = holder.withArk(block)

    override fun startSession(ark: ByteArray) {
        live = ark
        holder.set(ark)
        _isActive.value = true
        startSessionCalled = true
    }

    override fun endSession() {
        live = null
        holder.clear()
        _isActive.value = false
    }
}
