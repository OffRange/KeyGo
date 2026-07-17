package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordBackupOutcomeUseCaseTest {

    private val jobRepository = FakeBackupJobRepository()
    private val arkKeyStore = FakeBackupArkKeyStore()
    private val keyStoreManager = FakeKeyStoreManager()
    private val uriManager = FakePersistableUriManager()
    private val useCase = RecordBackupOutcomeUseCase(
        jobRepository = jobRepository,
        cleanupBackupResources = CleanupBackupResourcesUseCase(
            jobRepository = jobRepository,
            arkKeyStore = arkKeyStore,
            keyStoreManager = keyStoreManager,
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
        ),
    )

    private fun seed() {
        jobRepository.jobs["w"] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 1L,
        )
    }

    @Test
    fun `success records finishedAt and Success`() = runTest {
        seed()
        useCase("w", ExportProgress.Succeeded(itemCount = 3))

        val saved = jobRepository.jobs.getValue("w")
        assertNotNull(saved.finishedAt)
        assertEquals(BackupResult.Success, saved.lastResult)
        assertEquals(1L, saved.createdAt)
    }

    @Test
    fun `failure records Failure`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.WriteFailed))

        val saved = jobRepository.jobs.getValue("w")
        assertEquals(BackupResult.Failure, saved.lastResult)
        assertNotNull(saved.finishedAt)
    }

    @Test
    fun `session locked does not record`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.SessionLocked))

        assertNull(jobRepository.jobs.getValue("w").finishedAt)
    }

    @Test
    fun `null terminal does not record`() = runTest {
        seed()
        useCase("w", null)

        assertNull(jobRepository.jobs.getValue("w").finishedAt)
    }

    @Test
    fun `device locked does not record`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.DeviceLocked))

        val saved = jobRepository.jobs.getValue("w")
        assertNull(saved.finishedAt)
        assertNull(saved.lastResult)
        assertTrue(uriManager.released.isEmpty())
        assertTrue(keyStoreManager.keys.isEmpty())
    }

    @Test
    fun `a finished one-time backup releases its credentials`() = runTest {
        seed()
        useCase("w", ExportProgress.Succeeded(itemCount = 1))

        assertEquals(listOf(BackupDestinationUri("content://out.json")), uriManager.released)
    }

    @Test
    fun `a recurring run keeps its credentials for the next run`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 1L,
        )

        useCase(BackupWorker.RECURRING_WORK_ID, ExportProgress.Succeeded(itemCount = 1))

        assertTrue(uriManager.released.isEmpty())
    }

    @Test
    fun `a late outcome on a cancelled recurring schedule cleans nothing`() = runTest {
        val wrappedPassphrase = CryptographicData(data = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6))
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = wrappedPassphrase,
            format = FileFormat.JSON,
            createdAt = 1L,
            cancelled = true,
        )

        useCase(BackupWorker.RECURRING_WORK_ID, ExportProgress.Succeeded(itemCount = 1))

        assertTrue(uriManager.released.isEmpty())
        assertTrue(keyStoreManager.keys.isEmpty())
        val saved = jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID)
        assertNotNull(saved.wrappedPassphrase)
        assertContentEquals(wrappedPassphrase.data, saved.wrappedPassphrase.data)
        assertContentEquals(wrappedPassphrase.iv, saved.wrappedPassphrase.iv)
    }
}
