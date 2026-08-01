package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.BackupScheduler
import de.davis.keygo.feature.backup.domain.PersistableUriManager
import de.davis.keygo.feature.backup.domain.alias.WorkId
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Hands back everything a job needed once it stops needing it: its wrapped passphrase, its
 * persistable folder grant, and - once no job is live at all - the escrowed ARK copy and the
 * auth-less key aliases that protect both.
 *
 * Self-guarded: if `workId`'s record is still live, this does nothing. A recurring schedule's
 * record persists across runs and its next run reads its passphrase back out of it, so cleaning
 * up a still-live job would destroy credentials a future run needs.
 */
@Single
internal class CleanupBackupResourcesUseCase(
    private val jobRepository: BackupJobRepository,
    private val arkKeyStore: BackupArkKeyStore,
    private val keyStoreManager: KeyStoreManager,
    private val persistableUriManager: PersistableUriManager,
    private val provisioningLock: BackupProvisioningLock,
    private val scheduler: BackupScheduler,
) {

    suspend operator fun invoke(workId: WorkId): Unit = provisioningLock.mutex.withLock {
        val outstanding = outstandingWorkIds()
        val current = runCatching { jobRepository.getJobs() }.getOrNull() ?: return

        if (current[workId]?.isLive(workId, outstanding) == true) return

        sweep(current, outstanding)

        // Liveness and destination are read off the pre-sweep snapshot on purpose: clearing a
        // passphrase changes neither, and only the key release below cares what was cleared.
        val done = current[workId]
        val live = current.filter { (id, job) -> job.isLive(id, outstanding) }

        if (done != null && live.values.none { it.uri == done.uri })
            runCatching { persistableUriManager.releasePersistableUriPermission(done.uri) }
    }

    /**
     * Frees the escrow for work the scheduler no longer has, even though no run ever finished to
     * say so.
     *
     * [invoke] runs at the end of a dispatch. When the platform prunes or abandons the work
     * instead, that end never comes: the record still reads as pending, and the escrowed ARK stays
     * readable behind it forever. This checks every record against the scheduler's actual
     * outstanding work and releases what no live job needs any more.
     */
    suspend fun reconcile(): Unit = provisioningLock.mutex.withLock {
        val outstanding = outstandingWorkIds() ?: return
        val current = runCatching { jobRepository.getJobs() }.getOrNull() ?: return
        sweep(current, outstanding)
    }

    /**
     * Hands back the passphrase of every record the scheduler has no work for, then releases the
     * aliases that leaves unused.
     *
     * Keyed on liveness rather than on a single work id, because [reconcile] has no work id to
     * offer: the job it cleans up is one no dispatch ever ended, so nothing ever named it. Both
     * entry points share this so a dropped job cannot hand back its escrowed ARK while leaving the
     * wrapped passphrase - and the auth-less alias that opens it - behind.
     */
    private suspend fun sweep(current: Map<WorkId, BackupJob>, outstanding: Set<WorkId>?) {
        current.forEach { (id, job) ->
            if (job.wrappedPassphrase != null && !job.isLive(id, outstanding))
                runCatching { jobRepository.clearPassphrase(id) }
        }

        // Re-read: a failed clear must leave the record holding its passphrase, so the shared
        // alias below survives.
        val jobs = runCatching { jobRepository.getJobs() }.getOrNull() ?: return
        releaseUnusedKeys(jobs, jobs.filter { (id, job) -> job.isLive(id, outstanding) })
    }

    private suspend fun releaseUnusedKeys(
        jobs: Map<WorkId, BackupJob>,
        live: Map<WorkId, BackupJob>,
    ) {
        if (jobs.values.none { it.wrappedPassphrase != null })
            runCatching { keyStoreManager.deleteKey(KeyId.BackupPassphraseKey) }

        // A failed clear must leave the alias in place - otherwise the escrowed ciphertext
        // outlives the only key that can open it.
        if (live.isEmpty() && runCatching { arkKeyStore.clear() }.isSuccess)
            runCatching { keyStoreManager.deleteKey(KeyId.BackupArkKey) }
    }

    /** Null when the scheduler cannot be read - never an empty set, which would read as "nothing
     * is scheduled" and tear down credentials that are still in use. */
    private suspend fun outstandingWorkIds(): Set<WorkId>? =
        runCatching { scheduler.outstandingWorkIds() }.getOrNull()

    // A recurring schedule stamps finishedAt after every run, so only its absence - or cancellation
    // - ends it. A one-time job is live until it finishes. Either way a record counts as live only
    // while the scheduler still has work behind it: once it does not, no future run can read these
    // credentials. A null `outstanding` means the scheduler could not be read, so the record's own
    // bookkeeping is trusted and nothing is released on its account.
    private fun BackupJob.isLive(workId: WorkId, outstanding: Set<WorkId>?): Boolean =
        (outstanding == null || workId in outstanding) &&
                !cancelled &&
                (workId == BackupWorker.RECURRING_WORK_ID || finishedAt == null)
}
