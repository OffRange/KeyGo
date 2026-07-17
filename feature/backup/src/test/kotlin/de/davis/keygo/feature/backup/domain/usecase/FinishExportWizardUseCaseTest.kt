package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupScheduler
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.ExportDetails
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.FinishExportWizardError
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinishExportWizardUseCaseTest {

    private val scheduler = FakeBackupScheduler()
    private val persistable = FakePersistableUriManager()
    private val session = FakeSession(startOnConstruct = true)
    private val keyStoreManager = FakeKeyStoreManager()
    private val arkKeyStore = FakeBackupArkKeyStore()

    private fun useCase() = FinishExportWizardUseCase(
        backupScheduler = scheduler,
        keyStoreManager = keyStoreManager,
        persistableUriManager = persistable,
        session = session,
        arkKeyStore = arkKeyStore,
        provisioningLock = BackupProvisioningLock(),
    )

    private val uri = BackupDestinationUri("content://tree")

    // CSV keeps the passphrase/crypto path out of the picture; the mapping under test is
    // schedule/keepCount/permission, not encryption.
    private fun details(interval: BackupInterval? = null, keepCount: Int? = null) = ExportDetails(
        format = FileFormat.CSV,
        interval = interval,
        passphrase = "",
        uri = uri,
        keepCount = keepCount,
    )

    private fun jsonDetails(
        encryption: EncryptionMethod,
        passphrase: String = "",
    ) = ExportDetails(
        format = FileFormat.JSON,
        interval = null,
        passphrase = passphrase,
        uri = uri,
        encryption = encryption,
    )

    @Test
    fun `recurring backup persists keepCount and takes folder permission`() = runTest {
        val result = useCase()(
            details(interval = BackupInterval(count = 3, unit = IntervalUnit.Days), keepCount = 5),
        )

        assertIs<Result.Success<Unit, FinishExportWizardError>>(result)
        assertEquals(5, scheduler.recurringJob?.keepCount)
        assertEquals(listOf(uri), persistable.taken)
    }

    @Test
    fun `one-time backup takes folder permission and keeps all`() = runTest {
        val result = useCase()(details())

        assertIs<Result.Success<Unit, FinishExportWizardError>>(result)
        assertNull(scheduler.oneTimeJob?.keepCount)
        assertEquals(listOf(uri), persistable.taken)
    }

    @Test
    fun `permission failure returns DestinationPermissionDenied and does not schedule`() = runTest {
        persistable.throwOnTake = SecurityException("denied")

        val result = useCase()(
            details(interval = BackupInterval(count = 3, unit = IntervalUnit.Days), keepCount = 5),
        )

        val failure = assertIs<Result.Failure<Unit, FinishExportWizardError>>(result)
        assertEquals(FinishExportWizardError.DestinationPermissionDenied, failure.error)
        assertNull(scheduler.recurringJob)
    }

    @Test
    fun `configuring a backup provisions the ARK copy`() = runTest {
        useCase()(details(interval = BackupInterval(count = 3, unit = IntervalUnit.Days)))

        val wrapped = arkKeyStore.load()
        assertNotNull(wrapped)
        val recovered = keyStoreManager
            .getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Decrypt, wrapped.iv)
            .doFinal(wrapped.data)
        assertContentEquals(session.ark, recovered)
    }

    @Test
    fun `ark encryption schedules without a passphrase`() = runTest {
        val result = useCase()(jsonDetails(encryption = EncryptionMethod.Ark))

        assertIs<Result.Success<Unit, FinishExportWizardError>>(result)
        val job = assertNotNull(scheduler.oneTimeJob)
        assertEquals(EncryptionMethod.Ark, job.encryption)
        assertNull(job.wrappedPassphrase)
    }

    @Test
    fun `passphrase encryption with blank passphrase fails with PassphraseEmpty`() = runTest {
        val result = useCase()(jsonDetails(encryption = EncryptionMethod.Passphrase))

        val failure = assertIs<Result.Failure<Unit, FinishExportWizardError>>(result)
        assertEquals(FinishExportWizardError.PassphraseEmpty, failure.error)
    }

    @Test
    fun `passphrase encryption wraps the passphrase into the job`() = runTest {
        val result = useCase()(
            jsonDetails(encryption = EncryptionMethod.Passphrase, passphrase = "secret"),
        )

        assertIs<Result.Success<Unit, FinishExportWizardError>>(result)
        val job = assertNotNull(scheduler.oneTimeJob)
        assertEquals(EncryptionMethod.Passphrase, job.encryption)
        assertNotNull(job.wrappedPassphrase)
    }

    @Test
    fun `csv preset is carried onto the job`() = runTest {
        useCase()(details().copy(csvPreset = CsvPreset.KeyGo))

        assertEquals(CsvPreset.KeyGo, scheduler.oneTimeJob?.csvPreset)
    }

    @Test
    fun `a failed schedule releases the just-taken folder grant`() = runTest {
        // A failed schedule persists no record, so nothing will ever drive the grant's release -
        // the use case must release it itself to avoid leaking against the platform cap.
        scheduler.result = Result.Failure(Unit)

        val result = useCase()(details())

        val failure = assertIs<Result.Failure<Unit, FinishExportWizardError>>(result)
        assertEquals(FinishExportWizardError.SchedulePersistenceFailed, failure.error)
        assertEquals(listOf(uri), persistable.taken)
        assertEquals(listOf(uri), persistable.released)
    }

    @Test
    fun `a successful schedule keeps the folder grant`() = runTest {
        val result = useCase()(details())

        assertIs<Result.Success<Unit, FinishExportWizardError>>(result)
        assertEquals(listOf(uri), persistable.taken)
        assertTrue(persistable.released.isEmpty())
    }
}
