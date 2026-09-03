package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.ArkHolder
import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single
internal class SessionImpl : Session {

    private val holder = ArkHolder()
    private val _isActive = MutableStateFlow(false)

    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    override suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? = holder.withArk(block)

    override fun startSession(ark: ByteArray) {
        holder.set(ark)
        _isActive.value = true
    }

    /** [isActive] goes false at once even with a block in flight: the gate never waits on it. */
    override fun endSession() {
        holder.clear()
        _isActive.value = false
    }
}
