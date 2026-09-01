package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasskeyRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProviderFactory
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupFileStore
import de.davis.keygo.feature.backup.domain.BackupArkUnlocker
import de.davis.keygo.feature.backup.domain.BackupCollector
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupEntry
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.testLogin
import de.davis.keygo.feature.backup.testVault
import de.davis.keygo.rust.FakeCsvBackupManager
import de.davis.keygo.rust.FakeJsonBackupManager
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.ExportPreset
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportBackupUseCaseTest {

    private val vaultRepo = FakeVaultRepository()
    private val loginRepo = FakeLoginRepository()
    private val cardRepo = FakeCreditCardRepository()
    private val passkeyRepo = FakePasskeyRepository()
    private val scope = FakeCryptographicScopeProvider(FakeItemRepository())
    private val keyStore = FakeKeyStoreManager()
    private val arkStore = FakeBackupArkKeyStore()
    private val factory = FakeCryptographicScopeProviderFactory(scope)
    private val fileStore = FakeBackupFileStore()
    private val json = FakeJsonBackupManager()
    private val csv = FakeCsvBackupManager()

    private val folder = BackupDestinationUri("content://tree")

    private fun useCase(session: FakeSession): ExportBackupUseCase {
        val arkUnlocker = BackupArkUnlocker(session, keyStore, arkStore, factory, vaultRepo)
        return ExportBackupUseCase(
            collector = BackupCollector(
                vaultRepository = vaultRepo,
                loginRepository = loginRepo,
                creditCardRepository = cardRepo,
                passkeyRepository = passkeyRepo,
                arkUnlocker = arkUnlocker,
            ),
            fileStore = fileStore,
            jsonBackupManager = json,
            csvBackupManager = csv,
            keyStoreManager = keyStore,
            arkUnlocker = arkUnlocker,
        )
    }

    private suspend fun provision(session: FakeSession) {
        val cipher = keyStore.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        arkStore.save(CryptographicData(cipher.doFinal(assertNotNull(session.ark)), cipher.iv))
    }

    private val csvJob = BackupJob(
        uri = folder,
        wrappedPassphrase = null,
        format = FileFormat.CSV,
    )

    private fun unlocked() = FakeSession(startOnConstruct = true)

    private fun seedSingleLogin() {
        val vault = testVault(name = "V")
        vaultRepo.seed(vault)
        loginRepo.seed(testLogin(vaultId = vault.id, name = "Email", username = "alice"))
    }

    private fun seedExistingBackup(name: String) {
        fileStore.backups += BackupEntry(BackupDestinationUri("${folder.value}/$name"), name)
    }

    @Test
    fun `locked and unprovisioned session fails with NotProvisioned`() = runTest {
        seedSingleLogin()
        val emissions = useCase(FakeSession(startOnConstruct = false))(csvJob).toList()
        assertEquals(ExportProgress.Failed(ExportError.NotProvisioned), emissions.last())
    }

    @Test
    fun `locked but provisioned session exports successfully`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        provision(unlocked())
        val emissions = useCase(FakeSession(startOnConstruct = false))(csvJob).toList()
        assertIs<ExportProgress.Succeeded>(emissions.last())
    }

    @Test
    fun `csv export writes a timestamped document into the folder and succeeds`() = runTest {
        seedSingleLogin()
        csv.exportResult = "name,url,username,password,note\nEmail,,alice,,\n"

        val emissions = useCase(unlocked())(csvJob).toList()

        assertEquals(ExportProgress.Running(1, 1), emissions[0])
        assertEquals(ExportProgress.Writing, emissions[1])
        assertEquals(ExportProgress.Succeeded(1), emissions.last())
        assertEquals(csv.exportResult, fileStore.writtenText)
        assertEquals(folder, fileStore.writtenFolder)
        assertEquals(FileFormat.CSV.mimeType, fileStore.writtenMimeType)
        assertTrue(fileStore.writtenFileName!!.startsWith("keygo-backup-"))
        assertTrue(fileStore.writtenFileName!!.endsWith(".csv"))
    }

    @Test
    fun `empty database fails with NothingToExport`() = runTest {
        val emissions = useCase(unlocked())(csvJob).toList()
        val failed = assertIs<ExportProgress.Failed>(emissions.last())
        assertEquals(ExportError.NothingToExport, failed.error)
    }

    @Test
    fun `write failure surfaces WriteFailed`() = runTest {
        seedSingleLogin()
        fileStore.writeError = RuntimeException("disk full")

        val emissions = useCase(unlocked())(csvJob).toList()
        assertEquals(ExportError.WriteFailed, (emissions.last() as ExportProgress.Failed).error)
    }

    @Test
    fun `json job without passphrase fails with CryptoFailed`() = runTest {
        seedSingleLogin()
        val jsonJob = BackupJob(
            uri = folder,
            wrappedPassphrase = null,
            format = FileFormat.JSON,
        )

        val emissions = useCase(unlocked())(jsonJob).toList()

        val failed = assertIs<ExportProgress.Failed>(emissions.last())
        assertEquals(ExportError.CryptoFailed, failed.error)
    }

    @Test
    fun `passphrase decryption on a locked device fails with DeviceLocked`() = runTest {
        seedSingleLogin()
        val cipher =
            keyStore.getOrCreateCipherFor(KeyId.BackupPassphraseKey, CryptographicMode.Encrypt)
        val wrappedPassphrase =
            CryptographicData(cipher.doFinal("pw".encodeToByteArray()), cipher.iv)
        val jsonJob = BackupJob(
            uri = folder,
            wrappedPassphrase = wrappedPassphrase,
            format = FileFormat.JSON,
        )
        keyStore.deviceLocked = true

        val emissions = useCase(unlocked())(jsonJob).toList()

        val failed = assertIs<ExportProgress.Failed>(emissions.last())
        assertEquals(ExportError.DeviceLocked, failed.error)
    }

    @Test
    fun `ark json job seals with the session ark`() = runTest {
        seedSingleLogin()
        json.exportResult = "{}"
        val session = unlocked()
        val jsonJob = BackupJob(
            uri = folder,
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            encryption = EncryptionMethod.Ark,
        )

        val emissions = useCase(session)(jsonJob).toList()

        assertIs<ExportProgress.Succeeded>(emissions.last())
        val credential = assertIs<BackupCredential.Ark>(json.exportCalls.single().credential)
        assertContentEquals(session.ark, credential.key)
    }

    @Test
    fun `ark json job on a locked provisioned device uses the recovered ark`() = runTest {
        seedSingleLogin()
        json.exportResult = "{}"
        val unlockedSession = unlocked()
        provision(unlockedSession)
        val jsonJob = BackupJob(
            uri = folder,
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            encryption = EncryptionMethod.Ark,
        )

        val emissions = useCase(FakeSession(startOnConstruct = false))(jsonJob).toList()

        assertIs<ExportProgress.Succeeded>(emissions.last())
        val credential = assertIs<BackupCredential.Ark>(json.exportCalls.single().credential)
        assertContentEquals(unlockedSession.ark, credential.key)
    }

    @Test
    fun `csv job exports with its configured preset`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"

        useCase(unlocked())(csvJob.copy(csvPreset = CsvPreset.KeyGo)).toList()

        assertEquals(ExportPreset.KEY_GO, csv.exportCalls.single().preset)
    }

    @Test
    fun `csv job without preset falls back to Browser`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"

        useCase(unlocked())(csvJob).toList()

        assertEquals(ExportPreset.BROWSER, csv.exportCalls.single().preset)
    }

    @Test
    fun `csv serialization failure surfaces SerializationFailed`() = runTest {
        seedSingleLogin()
        csv.exportException = BackupException.EmptyCsv()

        val emissions = useCase(unlocked())(csvJob).toList()

        val failed = assertIs<ExportProgress.Failed>(emissions.last())
        assertIs<ExportError.SerializationFailed>(failed.error)
    }

    @Test
    fun `recurring job prunes backups beyond keepCount keeping the newest`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-2.csv")
        seedExistingBackup("keygo-backup-3.csv")

        useCase(unlocked())(csvJob.copy(keepCount = 2)).toList()

        assertEquals(
            setOf("${folder.value}/keygo-backup-1.csv", "${folder.value}/keygo-backup-2.csv"),
            fileStore.deleted.map { it.value }.toSet(),
        )
    }

    @Test
    fun `job without keepCount keeps all backups`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-2.csv")

        useCase(unlocked())(csvJob).toList()

        assertEquals(emptyList(), fileStore.deleted)
    }

    @Test
    fun `pruning ignores documents of a different format`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-1.json")

        useCase(unlocked())(csvJob.copy(keepCount = 1)).toList()

        assertEquals(
            listOf("${folder.value}/keygo-backup-1.csv"),
            fileStore.deleted.map { it.value })
    }

    @Test
    fun `pruning leaves files it did not write alone`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        // Shares the base name and extension but carries no epoch-millis stamp, so it is not a
        // document this app wrote - a user's own renamed copy, kept in the same folder.
        seedExistingBackup("keygo-backup-before-trip.csv")
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-2.csv")

        useCase(unlocked())(csvJob.copy(keepCount = 1)).toList()

        assertEquals(
            setOf("${folder.value}/keygo-backup-1.csv", "${folder.value}/keygo-backup-2.csv"),
            fileStore.deleted.map { it.value }.toSet(),
        )
    }

    @Test
    fun `pruning leaves a collision-renamed document alone`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        // What SAF produces when the name it is asked for is already taken. It parses as neither a
        // timestamp nor anything else orderable, so it must not be treated as the oldest backup.
        seedExistingBackup("keygo-backup-1700000000000 (1).csv")
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-2.csv")

        useCase(unlocked())(csvJob.copy(keepCount = 1)).toList()

        assertEquals(
            setOf("${folder.value}/keygo-backup-1.csv", "${folder.value}/keygo-backup-2.csv"),
            fileStore.deleted.map { it.value }.toSet(),
        )
    }

    @Test
    fun `prune failure does not fail a successful backup`() = runTest {
        seedSingleLogin()
        csv.exportResult = "data"
        seedExistingBackup("keygo-backup-1.csv")
        seedExistingBackup("keygo-backup-2.csv")
        fileStore.listError = RuntimeException("boom")

        val emissions = useCase(unlocked())(csvJob.copy(keepCount = 1)).toList()

        assertEquals(ExportProgress.Succeeded(1), emissions.last())
        assertEquals(emptyList(), fileStore.deleted)
    }
}
