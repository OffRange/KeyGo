package de.davis.keygo.feature.backup.data

import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A read-only [Session] holding a recovered ARK for the duration of a single backup. It never
 * mutates app-wide session state; [startSession] is unsupported and [endSession] is a no-op.
 */
internal class BackupSession(private val backupArk: ByteArray) : Session {

    override val ark: ByteArray get() = backupArk

    override val sessionStarts: Flow<Unit> = emptyFlow()

    override fun startSession(ark: ByteArray) =
        error("BackupSession is read-only")

    override fun endSession() = Unit
}
