package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakeBackupScheduler
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.worker.BackupWorker
import de.davisalessandro.keygo.rust.BackupException
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
    private val scheduler = FakeBackupScheduler(jobRepository)
    private val useCase = RecordBackupOutcomeUseCase(
        jobRepository = jobRepository,
        cleanupBackupResources = CleanupBackupResourcesUseCase(
            jobRepository = jobRepository,
            arkKeyStore = arkKeyStore,
            keyStoreManager = keyStoreManager,
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
            scheduler = scheduler,
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
        assertEquals(BackupResult.Failure(BackupFailureReason.WriteFailed), saved.lastResult)
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
    fun `device locked on the last attempt records RetriesExhausted`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.DeviceLocked), canRetry = false)

        val saved = jobRepository.jobs.getValue("w")
        assertNotNull(saved.finishedAt)
        assertEquals(BackupResult.Failure(BackupFailureReason.RetriesExhausted), saved.lastResult)
    }

    @Test
    fun `a spent one-time job releases its escrow instead of holding it for a retry`() = runTest {
        seed()
        arkKeyStore.save(CryptographicData(byteArrayOf(1), byteArrayOf(2)))

        useCase("w", ExportProgress.Failed(ExportError.DeviceLocked), canRetry = false)

        assertNull(arkKeyStore.load())
    }

    @Test
    fun `a finished one-time backup releases its credentials`() = runTest {
        seed()
        useCase("w", ExportProgress.Succeeded(itemCount = 1))

        assertEquals(listOf(BackupDestinationUri("content://out.json")), uriManager.released)
    }

    @Test
    fun `a recurring run keeps its credentials for the next run`() = runTest {
        val wrappedPassphrase =
            CryptographicData(data = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6))
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = wrappedPassphrase,
            format = FileFormat.JSON,
            createdAt = 1L,
        )

        useCase(BackupWorker.RECURRING_WORK_ID, ExportProgress.Succeeded(itemCount = 1))

        assertTrue(uriManager.released.isEmpty())
        // The schedule is still live, so the reconcile this outcome triggers must leave alone the
        // passphrase the next run reads back out of the record.
        val saved = jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID)
        assertNotNull(saved.wrappedPassphrase)
        assertContentEquals(wrappedPassphrase.data, saved.wrappedPassphrase.data)
        assertContentEquals(wrappedPassphrase.iv, saved.wrappedPassphrase.iv)
    }

    @Test
    fun `a late outcome on a cancelled recurring schedule hands back its passphrase`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase =
                CryptographicData(data = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6)),
            format = FileFormat.JSON,
            createdAt = 1L,
            cancelled = true,
        )

        useCase(BackupWorker.RECURRING_WORK_ID, ExportProgress.Succeeded(itemCount = 1))

        // Cancelled can never read as live again, so no run can ever need this passphrase. The
        // cancel path already clears it via CleanupBackupResourcesUseCase.invoke; a late outcome
        // arriving afterwards reaches the same conclusion instead of stranding it.
        assertNull(jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID).wrappedPassphrase)
        // reconcile still touches no folder grants - only invoke releases those.
        assertTrue(uriManager.released.isEmpty())
        assertTrue(keyStoreManager.keys.isEmpty())
    }

    @Test
    fun `a write failure records its reason`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.WriteFailed))

        assertEquals(
            BackupResult.Failure(BackupFailureReason.WriteFailed),
            jobRepository.jobs.getValue("w").lastResult,
        )
    }

    @Test
    fun `a serialization failure records the generic reason without the cause message`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.SerializationFailed(BackupException.Json("bad"))))

        assertEquals(
            BackupResult.Failure(BackupFailureReason.SerializationFailed),
            jobRepository.jobs.getValue("w").lastResult,
        )
    }

    @Test
    fun `a success leaves no reason behind`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.CryptoFailed))
        useCase("w", ExportProgress.Succeeded(itemCount = 3))

        assertEquals(BackupResult.Success, jobRepository.jobs.getValue("w").lastResult)
    }

    @Test
    fun `a retryable failure records no reason`() = runTest {
        seed()
        useCase("w", ExportProgress.Failed(ExportError.SessionLocked))

        assertNull(jobRepository.jobs.getValue("w").lastResult)
    }
}
