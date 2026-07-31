package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakeBackupScheduler
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The ARK escrow (`backup_ark_data.pb`) and the job records (`backup_jobs.pb`) are separate
 * DataStores with no cross-store transaction, and cleanup decides what to tear down purely from the
 * job records. A shared [BackupProvisioningLock] serializes the whole provision-and-schedule section
 * against the whole cleanup body so a cleanup can never observe a half-provisioned job (escrow
 * written, record not yet) and destroy credentials the new job needs.
 */
class BackupProvisioningSerializationTest {

    private val jobRepository = FakeBackupJobRepository()
    private val arkKeyStore =
        FakeBackupArkKeyStore(CryptographicData(byteArrayOf(7), byteArrayOf(8)))
    private val keyStoreManager = FakeKeyStoreManager()
    private val uriManager = FakePersistableUriManager()
    private val session = FakeSession(startOnConstruct = true)
    private val lock = BackupProvisioningLock()
    private val scheduler = FakeBackupScheduler(jobRepository)

    // Provisioning parks here after saving B's escrow but before B's record is written.
    private val gate = CompletableDeferred<Unit>()

    private val finish = FinishExportWizardUseCase(
        backupScheduler = FakeBackupScheduler(
            jobRepository = jobRepository,
            gate = gate,
            oneTimeWorkId = "B",
        ),
        keyStoreManager = keyStoreManager,
        persistableUriManager = uriManager,
        session = session,
        arkKeyStore = arkKeyStore,
        provisioningLock = lock,
    )

    private val cleanup = CleanupBackupResourcesUseCase(
        jobRepository = jobRepository,
        arkKeyStore = arkKeyStore,
        keyStoreManager = keyStoreManager,
        persistableUriManager = uriManager,
        provisioningLock = lock,
        scheduler = scheduler,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cleanup cannot tear down a job that is concurrently mid-provisioning`() = runTest {
        // Job A: a finished one-time job holding no passphrase - non-live, nothing keeps the shared
        // credentials alive, so a cleanup that races the provisioning of B would strip them.
        jobRepository.jobs["A"] = BackupJob(
            uri = BackupDestinationUri("content://A"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            finishedAt = 1L,
        )

        // Provision job B (passphrase-encrypted): it takes the lock, creates BackupPassphraseKey and
        // BackupArkKey, saves B's escrow, then parks at the gate with B's record not yet written.
        val provisioning = launch {
            finish(
                ExportDetails(
                    format = FileFormat.JSON,
                    interval = null,
                    passphrase = "secret",
                    uri = BackupDestinationUri("content://B"),
                    encryption = EncryptionMethod.Passphrase,
                ),
            )
        }
        runCurrent()

        // Concurrent cleanup of the already-finished A. With the lock held it must block and tear
        // nothing down: B's escrow and both shared aliases must still be intact.
        val cleaning = launch { cleanup("A") }
        runCurrent()

        assertNotNull(arkKeyStore.load())
        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)

        // Release provisioning: it writes B's live record and drops the lock, then cleanup runs and,
        // seeing B live, spares the escrow and both aliases.
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(provisioning.isCompleted)
        assertTrue(cleaning.isCompleted)
        assertNotNull(arkKeyStore.load())
        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }
}
