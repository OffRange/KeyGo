package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.FakeBackupFileStore
import de.davis.keygo.feature.backup.RestorerTestEnv
import de.davis.keygo.feature.backup.backupVault
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportRequest
import de.davis.keygo.feature.backup.domain.model.ImportTarget
import de.davis.keygo.feature.backup.testVault
import de.davis.keygo.rust.FakeCsvBackupManager
import de.davis.keygo.rust.FakeJsonBackupManager
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvImportResult
import de.davisalessandro.keygo.rust.FieldConfidence
import de.davisalessandro.keygo.rust.ImportReport
import de.davisalessandro.keygo.rust.JsonEncryption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportBackupUseCaseTest {

    private val env = RestorerTestEnv()
    private val fileStore = FakeBackupFileStore()
    private val json = FakeJsonBackupManager()
    private val csv = FakeCsvBackupManager()

    private fun useCase(session: FakeSession = FakeSession(startOnConstruct = true)) =
        ImportBackupUseCase(fileStore, json, csv, env.restorer, session)

    private fun jsonRequest(passphrase: String? = "pw") = ImportRequest(
        uri = BackupDestinationUri("content://in.json"),
        format = FileFormat.JSON,
        passphrase = passphrase,
    )

    private fun login(title: String) = BackupLogin(
        title = title,
        notes = null,
        tags = emptyList(),
        pinned = false,
        username = null,
        password = "pw",
        totpSecret = null,
        websites = emptyList(),
        passkeys = emptyList(),
    )

    @Test
    fun `locked session fails fast`() = runTest {
        val emissions = useCase(FakeSession(startOnConstruct = false))(jsonRequest()).toList()
        assertEquals(listOf(ImportProgress.Failed(ImportError.SessionLocked)), emissions)
    }

    @Test
    fun `unreadable file emits FileUnreadable`() = runTest {
        fileStore.readError = IllegalStateException("disk error")

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Reading, emissions[0])
        assertEquals(ImportProgress.Failed(ImportError.FileUnreadable), emissions[1])
    }

    @Test
    fun `blank file emits EmptyFile`() = runTest {
        fileStore.contents = "   "

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Reading, emissions[0])
        assertEquals(ImportProgress.Failed(ImportError.EmptyFile), emissions[1])
    }

    @Test
    fun `json parse failure maps CredentialMismatch to WrongCredential`() = runTest {
        fileStore.contents = """{"vaults":[]}"""
        json.importException = BackupException.CredentialMismatch()

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Parsing, emissions[1])
        assertEquals(ImportProgress.Failed(ImportError.WrongCredential), emissions.last())
    }

    @Test
    fun `json parse failure maps Crypto to WrongCredential`() = runTest {
        fileStore.contents = """{"vaults":[]}"""
        json.importException = BackupException.Crypto("bad key")

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Failed(ImportError.WrongCredential), emissions.last())
    }

    @Test
    fun `json parse failure maps Json to ParseFailed`() = runTest {
        fileStore.contents = """not-json"""
        val cause = BackupException.Json("bad json")
        json.importException = cause

        val emissions = useCase()(jsonRequest()).toList()

        val failed = assertIs<ImportProgress.Failed>(emissions.last())
        assertEquals(ImportError.ParseFailed(cause), failed.error)
    }

    @Test
    fun `json import restores and succeeds`() = runTest {
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(listOf(backupVault("Imported", listOf(login("Email")))))

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Reading, emissions[0])
        assertEquals(ImportProgress.Parsing, emissions[1])
        val success = assertIs<ImportProgress.Succeeded>(emissions.last())
        assertEquals(1, success.summary.imported)
    }

    @Test
    fun `csv import uses the suggested mapping`() = runTest {
        fileStore.contents = "name,username\nEmail,alice\n"
        csv.analyzeResult = CsvAnalysis(
            columns = emptyList(),
            suggested = ColumnMapping(0u, null, 1u, null, null, null),
            confidence = FieldConfidence(null, null, null, null, null, null),
        )
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )

        val emissions = useCase()(
            ImportRequest(BackupDestinationUri("content://in.csv"), FileFormat.CSV, null),
        ).toList()

        val success = assertIs<ImportProgress.Succeeded>(emissions.last())
        assertEquals(1, success.summary.imported)
        assertEquals(csv.analyzeResult.suggested, csv.importCalls.last().mapping)
    }

    @Test
    fun `csv import prefers the caller-provided mapping over the suggestion`() = runTest {
        fileStore.contents = "name,secret\nEmail,alice\n"
        csv.analyzeResult = CsvAnalysis(
            columns = emptyList(),
            suggested = ColumnMapping(0u, null, 1u, null, null, null), // suggests username=1
            confidence = FieldConfidence(null, null, null, null, null, null),
        )
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val edited = ColumnMapping(0u, null, null, 1u, null, null) // user says col 1 = password

        val emissions = useCase()(
            ImportRequest(
                uri = BackupDestinationUri("content://in.csv"),
                format = FileFormat.CSV,
                passphrase = null,
                csvMapping = edited,
            ),
        ).toList()

        assertIs<ImportProgress.Succeeded>(emissions.last())
        assertEquals(edited, csv.importCalls.last().mapping)
    }

    @Test
    fun `csv analyze failure maps CredentialMismatch to WrongCredential`() = runTest {
        fileStore.contents = "name\nEmail\n"
        csv.analyzeException = BackupException.CredentialMismatch()

        val emissions = useCase()(
            ImportRequest(BackupDestinationUri("content://in.csv"), FileFormat.CSV, null),
        ).toList()

        assertEquals(ImportProgress.Failed(ImportError.WrongCredential), emissions.last())
    }

    @Test
    fun `empty backup from restorer emits NothingImported`() = runTest {
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(emptyList())

        val emissions = useCase()(jsonRequest()).toList()

        assertEquals(ImportProgress.Failed(ImportError.NothingImported), emissions.last())
    }

    @Test
    fun `running progress is emitted during restore`() = runTest {
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(
            listOf(backupVault("V", listOf(login("A"), login("B")))),
        )

        val emissions = useCase()(jsonRequest()).toList()

        val running = emissions.filterIsInstance<ImportProgress.Running>()
        assertEquals(2, running.size)
        assertEquals(ImportProgress.Running(1, 2), running[0])
        assertEquals(ImportProgress.Running(2, 2), running[1])
    }

    @Test
    fun `ark-sealed json imports with the session ark`() = runTest {
        fileStore.contents = "{}"
        json.inspectResult = JsonEncryption.ARK
        json.importResult = Backup(listOf(backupVault("V", listOf(login("A")))))
        val session = FakeSession(startOnConstruct = true)

        val emissions = useCase(session)(jsonRequest(passphrase = null)).toList()

        assertIs<ImportProgress.Succeeded>(emissions.last())
        val credential = assertIs<BackupCredential.Ark>(json.importCalls.single().credential)
        assertContentEquals(session.currentArk, credential.key)
    }

    @Test
    fun `passphrase bytes are zeroed once the import returns`() = runTest {
        fileStore.contents = "{}"
        json.inspectResult = JsonEncryption.PASSPHRASE
        json.importResult = Backup(listOf(backupVault("V", listOf(login("A")))))
        // Hold the array the use case hands to Rust, rather than the fake's defensive copy.
        var handed: ByteArray? = null
        json.onImport = { _, credential ->
            handed = (credential as BackupCredential.Passphrase).bytes
        }

        val emissions = useCase()(jsonRequest(passphrase = "hunter2")).toList()

        assertIs<ImportProgress.Succeeded>(emissions.last())
        assertContentEquals(ByteArray(7), handed)
    }

    @Test
    fun `passphrase-sealed json without a passphrase fails with PassphraseRequired`() = runTest {
        fileStore.contents = "{}"
        json.inspectResult = JsonEncryption.PASSPHRASE

        val emissions = useCase()(jsonRequest(passphrase = null)).toList()

        assertEquals(ImportProgress.Failed(ImportError.PassphraseRequired), emissions.last())
        assertEquals(emptyList(), json.importCalls)
    }

    @Test
    fun `unreadable header surfaces as import failure`() = runTest {
        fileStore.contents = "{}"
        json.inspectException = BackupException.MalformedHeader()

        val emissions = useCase()(jsonRequest()).toList()

        assertIs<ImportProgress.Failed>(emissions.last())
    }

    @Test
    fun `session locked between read and parse fails with SessionLocked instead of throwing`() =
        runTest {
            val session = FakeSession(startOnConstruct = true)
            fileStore.contents = "{}"
            json.inspectResult = JsonEncryption.ARK
            val lockDuringRead = object : BackupFileStore by fileStore {
                override suspend fun read(uri: BackupDestinationUri): Result<String, Throwable> {
                    val result = fileStore.read(uri)
                    session.endSession()
                    return result
                }
            }

            val emissions = ImportBackupUseCase(lockDuringRead, json, csv, env.restorer, session)(
                jsonRequest(passphrase = null),
            ).toList()

            assertEquals(
                listOf(
                    ImportProgress.Reading,
                    ImportProgress.Parsing,
                    ImportProgress.Failed(ImportError.SessionLocked),
                ),
                emissions,
            )
        }

    @Test
    fun `a request targeting an existing vault imports into it`() = runTest {
        val existing = testVault(name = "Personal")
        env.vaultRepo.seed(existing)
        fileStore.contents = "name,username\nEmail,alice\n"
        csv.analyzeResult = CsvAnalysis(
            columns = emptyList(),
            suggested = ColumnMapping(0u, null, 1u, null, null, null),
            confidence = FieldConfidence(null, null, null, null, null, null),
        )
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )

        val emissions = useCase()(
            ImportRequest(
                uri = BackupDestinationUri("content://in.csv"),
                format = FileFormat.CSV,
                passphrase = null,
                target = ImportTarget.Existing(existing.id),
            ),
        ).toList()

        val success = assertIs<ImportProgress.Succeeded>(emissions.last())
        assertEquals(1, success.summary.imported)
        assertEquals(0, success.summary.vaultsCreated)
        assertEquals(1, env.loginRepo.getLoginsByVault(existing.id).size)
    }

    @Test
    fun `a request targeting a new vault creates it and ignores the parsed vault name`() = runTest {
        fileStore.contents = "name,username\nEmail,alice\n"
        csv.analyzeResult = CsvAnalysis(
            columns = emptyList(),
            suggested = ColumnMapping(0u, null, 1u, null, null, null),
            confidence = FieldConfidence(null, null, null, null, null, null),
        )
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )

        val emissions = useCase()(
            ImportRequest(
                uri = BackupDestinationUri("content://in.csv"),
                format = FileFormat.CSV,
                passphrase = null,
                target = ImportTarget.New("passwords"),
            ),
        ).toList()

        val success = assertIs<ImportProgress.Succeeded>(emissions.last())
        assertEquals(1, success.summary.vaultsCreated)
        assertEquals(
            listOf("passwords"),
            env.vaultRepo.observeAllVaultMetadata().first().map { it.name },
        )
    }
}
