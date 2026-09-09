package de.davis.keygo.feature.backup.data

import de.davis.keygo.core.security.domain.LegacySession
import de.davisalessandro.keygo.rust.ArkSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A read-only [LegacySession] holding a recovered ARK for the duration of a single backup. It
 * never mutates app-wide session state; [startSession] is unsupported and [endSession] is a
 * no-op.
 */
internal class BackupSession(private val backupArk: ByteArray) : LegacySession {

    override val isActive: StateFlow<Boolean> = MutableStateFlow(true)

    /** Always runs [block]: the ARK was already recovered, and whoever recovered it wipes it. */
    override suspend fun <R> withArk(block: suspend (ByteArray) -> R): R? = block(backupArk)

    override fun startSession(ark: ByteArray) =
        error("BackupSession is read-only")

    override fun endSession() = Unit
}

/** Temporary bridge: Task 5 replaces the ByteArray plumbing with a Session throughout. */
internal fun arkSession(ark: ByteArray): ArkSession = ArkSession().apply { unlockWithArk(ark) }
