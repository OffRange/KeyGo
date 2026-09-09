package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.ArkHolder
import de.davis.keygo.core.security.domain.LegacySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * A fake [LegacySession] with a fixed ARK. Shares [ArkHolder] with the real one, so it wipes the
 * same way - a fake that skipped the wipe would hide use-after-wipe bugs from every test.
 */
class FakeSession(
    startOnConstruct: Boolean = false
) : LegacySession {

    var startSessionCalled = false

    private val holder = ArkHolder()
    private val _isActive = MutableStateFlow(false)

    /**
     * The live ARK as a copy, for assertions. Null once the session has ended. Goes through
     * [ArkHolder.withArk] like any other reader - `runBlocking` only bridges the suspend call for
     * a synchronous test property, it does not bypass the reader accounting the way a raw peek
     * would.
     */
    val currentArk: ByteArray?
        get() = runBlocking { holder.withArk { it.copyOf() } }

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
        holder.set(ark)
        _isActive.value = true
        startSessionCalled = true
    }

    override fun endSession() {
        holder.clear()
        _isActive.value = false
    }
}
