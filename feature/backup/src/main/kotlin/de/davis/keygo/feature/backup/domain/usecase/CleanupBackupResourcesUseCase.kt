package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.PersistableUriManager
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
) {

    suspend operator fun invoke(workId: String): Unit = provisioningLock.mutex.withLock {
        val current = runCatching { jobRepository.getJobs() }.getOrNull() ?: return

        if (current[workId]?.isLive(workId) == true) return

        runCatching { jobRepository.clearPassphrase(workId) }

        // Re-read: a failed clear must leave the record holding its passphrase, so the shared
        // alias below survives.
        val jobs = runCatching { jobRepository.getJobs() }.getOrNull() ?: return
        val done = jobs[workId]
        val live = jobs.filter { (id, job) -> job.isLive(id) }

        if (done != null && live.values.none { it.uri == done.uri })
            runCatching { persistableUriManager.releasePersistableUriPermission(done.uri) }

        if (jobs.values.none { it.wrappedPassphrase != null })
            runCatching { keyStoreManager.deleteKey(KeyId.BackupPassphraseKey) }

        // A failed clear must leave the alias in place - otherwise the escrowed ciphertext
        // outlives the only key that can open it.
        if (live.isEmpty() && runCatching { arkKeyStore.clear() }.isSuccess)
            runCatching { keyStoreManager.deleteKey(KeyId.BackupArkKey) }
    }

    // A recurring schedule stamps finishedAt after every run, so only its absence - or cancellation
    // - ends it. A one-time job is live until it finishes.
    private fun BackupJob.isLive(workId: String): Boolean =
        !cancelled && (workId == BackupWorker.RECURRING_WORK_ID || finishedAt == null)
}
