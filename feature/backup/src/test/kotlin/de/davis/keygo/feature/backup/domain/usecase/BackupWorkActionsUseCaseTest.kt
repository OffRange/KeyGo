package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakeBackupScheduler
import de.davis.keygo.feature.backup.FakeDispatchedBackupRepository
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupWorkActionsUseCaseTest {

    private val repository = FakeDispatchedBackupRepository()
    private val jobRepository = FakeBackupJobRepository()
    private val uriManager = FakePersistableUriManager()
    private val scheduler = FakeBackupScheduler(jobRepository)

    private val cancelBackup = CancelBackupUseCase(
        repository = repository,
        jobRepository = jobRepository,
        cleanupBackupResources = CleanupBackupResourcesUseCase(
            jobRepository = jobRepository,
            arkKeyStore = FakeBackupArkKeyStore(),
            keyStoreManager = FakeKeyStoreManager(),
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
            scheduler = scheduler,
        ),
    )

    private fun job() = BackupJob(
        uri = BackupDestinationUri("content://folder"),
        wrappedPassphrase = null,
        format = FileFormat.JSON,
    )

    @Test
    fun `cancel forwards the id to the repository`() = runTest {
        jobRepository.jobs["work-42"] = job()

        cancelBackup("work-42", DispatchedBackup.Kind.OneTime)

        assertEquals(listOf("work-42"), repository.cancelledIds)
    }

    @Test
    fun `cancelling a one-time backup marks its record and frees the folder`() = runTest {
        jobRepository.jobs["work-42"] = job()

        cancelBackup("work-42", DispatchedBackup.Kind.OneTime)

        assertTrue(jobRepository.jobs.getValue("work-42").cancelled)
        assertEquals(listOf(BackupDestinationUri("content://folder")), uriManager.released)
    }

    @Test
    fun `cancelling a recurring backup marks the recurring record, not the work id`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = job()

        cancelBackup("work-uuid", DispatchedBackup.Kind.Recurring)

        assertTrue(jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID).cancelled)
    }
}
