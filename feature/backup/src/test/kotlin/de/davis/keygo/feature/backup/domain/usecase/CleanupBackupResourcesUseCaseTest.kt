package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import de.davis.keygo.feature.backup.domain.repository.BackupJobRepository
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanupBackupResourcesUseCaseTest {

    private val jobRepository = FakeBackupJobRepository()
    private val arkKeyStore =
        FakeBackupArkKeyStore(CryptographicData(byteArrayOf(1), byteArrayOf(2)))
    private val keyStoreManager = FakeKeyStoreManager()
    private val uriManager = FakePersistableUriManager()

    private val useCase = CleanupBackupResourcesUseCase(
        jobRepository = jobRepository,
        arkKeyStore = arkKeyStore,
        keyStoreManager = keyStoreManager,
        persistableUriManager = uriManager,
        provisioningLock = BackupProvisioningLock(),
    )

    private val folder = BackupDestinationUri("content://folder")

    private fun job(
        uri: BackupDestinationUri = folder,
        wrapped: CryptographicData? = CryptographicData(byteArrayOf(9), byteArrayOf(8)),
        finishedAt: Long? = null,
        cancelled: Boolean = false,
    ) = BackupJob(
        uri = uri,
        wrappedPassphrase = wrapped,
        format = FileFormat.JSON,
        finishedAt = finishedAt,
        cancelled = cancelled,
    )

    /** The fake only ever removes a key on [FakeKeyStoreManager.deleteKey]; it never creates one
     * implicitly, so a test asserting a key survives cleanup must seed it first. */
    private fun seedKey(keyId: KeyId) {
        keyStoreManager.getOrCreateCipherFor(keyId, CryptographicMode.Encrypt)
    }

    @Test
    fun `a finished one-time job releases every credential it held`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)

        useCase("w")

        assertNull(jobRepository.jobs.getValue("w").wrappedPassphrase)
        assertEquals(listOf(folder), uriManager.released)
        assertNull(arkKeyStore.load())
        assertTrue(KeyId.BackupArkKey !in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey !in keyStoreManager.keys)
    }

    @Test
    fun `a live recurring schedule keeps the ark escrow`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        // A recurring record is live even with finishedAt stamped: it is set after every run.
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] =
            job(uri = BackupDestinationUri("content://other"), finishedAt = 5L)
        seedKey(KeyId.BackupArkKey)
        seedKey(KeyId.BackupPassphraseKey)

        useCase("w")

        val escrow = arkKeyStore.load()
        assertContentEquals(byteArrayOf(1), escrow?.data)
        assertContentEquals(byteArrayOf(2), escrow?.iv)
        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        // The recurring job still holds a wrapped passphrase, so its key must survive too.
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }

    @Test
    fun `cleanup on a still-live recurring schedule leaves everything untouched`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = job(finishedAt = 5L)

        useCase(BackupWorker.RECURRING_WORK_ID)

        assertEquals(
            CryptographicData(byteArrayOf(9), byteArrayOf(8)),
            jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID).wrappedPassphrase,
        )
        assertTrue(uriManager.released.isEmpty())
        assertTrue(keyStoreManager.keys.isEmpty())
        val escrow = arkKeyStore.load()
        assertContentEquals(byteArrayOf(1), escrow?.data)
        assertContentEquals(byteArrayOf(2), escrow?.iv)
    }

    @Test
    fun `the folder grant survives while a live job still targets it`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        jobRepository.jobs["pending"] = job(finishedAt = null)

        useCase("w")

        assertTrue(uriManager.released.isEmpty())
    }

    @Test
    fun `a job cancelled before it ran is not live and frees the escrow`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = null, cancelled = true)

        useCase("w")

        assertNull(arkKeyStore.load())
        assertTrue(KeyId.BackupArkKey !in keyStoreManager.keys)
    }

    @Test
    fun `a cancelled recurring schedule is not live and frees everything`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = job(cancelled = true)

        useCase(BackupWorker.RECURRING_WORK_ID)

        assertNull(jobRepository.jobs.getValue(BackupWorker.RECURRING_WORK_ID).wrappedPassphrase)
        assertEquals(listOf(folder), uriManager.released)
        assertNull(arkKeyStore.load())
        assertTrue(KeyId.BackupArkKey !in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey !in keyStoreManager.keys)
    }

    @Test
    fun `the passphrase key survives while another record still holds one`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        jobRepository.jobs["pending"] = job(finishedAt = null)
        seedKey(KeyId.BackupPassphraseKey)

        useCase("w")

        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }

    @Test
    fun `cleanup returns normally even when reading jobs throws`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        val failingReads = object : BackupJobRepository by jobRepository {
            override suspend fun getJobs(): Map<String, BackupJob> = throw IOException("boom")
        }
        val useCase = CleanupBackupResourcesUseCase(
            jobRepository = failingReads,
            arkKeyStore = arkKeyStore,
            keyStoreManager = keyStoreManager,
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
        )

        useCase("w")

        assertTrue(keyStoreManager.keys.isEmpty())
        assertTrue(uriManager.released.isEmpty())
        val escrow = arkKeyStore.load()
        assertContentEquals(byteArrayOf(1), escrow?.data)
        assertContentEquals(byteArrayOf(2), escrow?.iv)
    }

    @Test
    fun `a failed ark clear leaves the ark key alone`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        val failingClear = object : BackupArkKeyStore by arkKeyStore {
            override suspend fun clear(): Unit = throw IOException("boom")
        }
        val useCase = CleanupBackupResourcesUseCase(
            jobRepository = jobRepository,
            arkKeyStore = failingClear,
            keyStoreManager = keyStoreManager,
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
        )
        seedKey(KeyId.BackupArkKey)

        useCase("w")

        // clear() failed, so the escrowed ciphertext is still on disk - the alias that opens it
        // must not be deleted underneath it.
        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        val escrow = arkKeyStore.load()
        assertContentEquals(byteArrayOf(1), escrow?.data)
        assertContentEquals(byteArrayOf(2), escrow?.iv)
    }

    @Test
    fun `a failed passphrase clear leaves the passphrase key alone`() = runTest {
        jobRepository.jobs["w"] = job(finishedAt = 1L)
        val failingClear = object : BackupJobRepository by jobRepository {
            override suspend fun clearPassphrase(workId: String): Unit = throw IOException("boom")
        }
        val useCase = CleanupBackupResourcesUseCase(
            jobRepository = failingClear,
            arkKeyStore = arkKeyStore,
            keyStoreManager = keyStoreManager,
            persistableUriManager = uriManager,
            provisioningLock = BackupProvisioningLock(),
        )
        seedKey(KeyId.BackupPassphraseKey)

        useCase("w")

        // clearPassphrase failed, so the record still holds a wrapped passphrase and getJobs
        // correctly reports it - the alias must not be deleted underneath it.
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }
}
