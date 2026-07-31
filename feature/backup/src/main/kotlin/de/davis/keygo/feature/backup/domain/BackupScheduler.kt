package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.BackupJob

interface BackupScheduler {

    suspend fun scheduleRecurringBackup(
        job: BackupJob,
        interval: BackupInterval
    ): Result<Unit, Unit>

    suspend fun scheduleOneTimeBackup(job: BackupJob): Result<Unit, Unit>
    fun cancel()

    /**
     * Work ids the scheduler still has outstanding - enqueued, blocked, or running.
     *
     * This is the ground truth for whether a job record can still produce a run. A record that
     * looks live by its own bookkeeping but has no outstanding work behind it can never run again,
     * so nothing may keep holding its credentials open.
     *
     * Throws if the scheduler cannot be read; callers must treat that as "unknown" and release
     * nothing, never as "no work outstanding".
     */
    suspend fun outstandingWorkIds(): Set<WorkId>
}