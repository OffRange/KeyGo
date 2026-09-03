package de.davis.keygo.feature.backup.data

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A read-only [Session] holding a recovered ARK for the duration of a single backup. It never
 * mutates app-wide session state; [startSession] is unsupported and [endSession] is a no-op.
 */
internal class BackupSession(private val backupArk: ByteArray) : Session {

    override val isActive: StateFlow<Boolean> = MutableStateFlow(true)

    /** Always runs [block]: the ARK was already recovered, and whoever recovered it wipes it. */
    override suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? = block(backupArk)

    override fun startSession(ark: ByteArray) =
        error("BackupSession is read-only")

    override fun endSession() = Unit
}
