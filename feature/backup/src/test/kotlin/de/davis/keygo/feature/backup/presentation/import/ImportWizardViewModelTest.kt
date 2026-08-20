package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.backup.FakeBackupFileStore
import de.davis.keygo.feature.backup.RestorerTestEnv
import de.davis.keygo.feature.backup.backupVault
import de.davis.keygo.feature.backup.data.FakeBackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.usecase.AnalyzeCsvUseCase
import de.davis.keygo.feature.backup.domain.usecase.ImportBackupUseCase
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardEvent
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardStep
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiEvent
import de.davis.keygo.feature.backup.testVault
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import de.davis.keygo.rust.FakeCsvBackupManager
import de.davis.keygo.rust.FakeJsonBackupManager
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCredential
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.Confidence
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvColumn
import de.davisalessandro.keygo.rust.CsvImportResult
import de.davisalessandro.keygo.rust.FieldConfidence
import de.davisalessandro.keygo.rust.ImportReport
import de.davisalessandro.keygo.rust.JsonEncryption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWizardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val env = RestorerTestEnv()
    private val fileStore = FakeBackupFileStore()
    private val json = FakeJsonBackupManager()
    private val csv = FakeCsvBackupManager()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun jsonDestination() = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
        fileName = "keygo.json",
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

    /**
     * `state` is `WhileSubscribed`, so it only tracks `_state` while something collects it. Tests
     * that read `viewModel.state.value` right after an event need that subscription to exist.
     */
    private fun TestScope.viewModel(
        resolver: FakeBackupDestinationResolver = FakeBackupDestinationResolver(),
        session: FakeSession = FakeSession(startOnConstruct = true),
        contextRepo: FakeVaultContextRepository = FakeVaultContextRepository(),
    ) = ImportWizardViewModel(
        resolver,
        ImportBackupUseCase(fileStore, json, csv, env.restorer, session),
        AnalyzeCsvUseCase(fileStore, csv),
        ObserveVaultsAndSelectionUseCase(env.vaultRepo, contextRepo, SortUseCase()),
    ).also { it.state.launchIn(backgroundScope) }

    private fun ImportWizardViewModel.selectJson() {
        onFilePicked(BackupDestinationUri("content://doc/keygo.json"))
    }

    private suspend fun ImportWizardViewModel.advanceToMapColumns() {
        onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        state.first { it.backupDestination != null }
        onEvent(ImportWizardUiEvent.Continue)
        state.first { it.step == ImportWizardStep.MapColumns }
    }

    private fun csvDestination() = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
        fileName = "keygo.csv",
    )

    private fun textDestination() = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
        fileName = "notes.txt",
    )

    private fun csvAnalysis() = CsvAnalysis(
        columns = listOf(
            CsvColumn(0u, "name", listOf("Email")),
            CsvColumn(1u, "secret", listOf("s3cr3t")),
        ),
        suggested = ColumnMapping(0u, null, null, 1u, null, null),
        confidence = FieldConfidence(
            Confidence.HIGH, null, null,
            Confidence.HIGH, null, null,
        ),
    )

    @Test
    fun `ChooseFile emits PickFile event`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(ImportWizardUiEvent.ChooseFile)

        assertEquals(ImportWizardEvent.PickFile, viewModel.event.first())
    }

    @Test
    fun `onFilePicked resolves and stores destination and uri`() = runTest {
        val destination = jsonDestination()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = destination))
        val uri = BackupDestinationUri("content://doc/keygo.json")

        viewModel.onFilePicked(uri)
        advanceUntilIdle()

        assertEquals(destination, viewModel.state.value.backupDestination)
        assertEquals(uri, viewModel.state.value.uri)
    }

    @Test
    fun `onFilePicked with null leaves state unchanged`() = runTest {
        val viewModel = viewModel()

        viewModel.onFilePicked(null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.backupDestination)
        assertNull(viewModel.state.value.uri)
    }

    @Test
    fun `Continue on selected JSON runs import and surfaces the summary`() = runTest {
        // ARK-sealed: the one JSON shape that imports straight through without a passphrase step.
        json.inspectResult = JsonEncryption.ARK
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(listOf(backupVault("Imported", listOf(login("Email")))))
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))
        viewModel.selectJson()
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Succeeded }

        val succeeded = assertIs<ImportProgress.Succeeded>(finalState.progress)
        assertEquals(1, succeeded.summary.imported)
        assertIs<BackupCredential.Ark>(json.importCalls.single().credential)
    }

    @Test
    fun `passphrase-encrypted backup advances to the passphrase step`() = runTest {
        json.inspectResult = JsonEncryption.PASSPHRASE
        fileStore.contents = """{"vaults":[]}"""
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))
        viewModel.selectJson()
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.step == ImportWizardStep.ProvidePassphrase }

        assertNull(finalState.progress)
    }

    @Test
    fun `wrong passphrase keeps the passphrase step and flags the error`() = runTest {
        json.inspectResult = JsonEncryption.PASSPHRASE
        fileStore.contents = """{"vaults":[]}"""
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))
        viewModel.selectJson()
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.ProvidePassphrase }

        json.importException = BackupException.CredentialMismatch()
        viewModel.state.value.passphraseState.setTextAndPlaceCursorAtEnd("hunter2")
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.passphraseError }

        assertEquals(ImportWizardStep.ProvidePassphrase, finalState.step)
        assertTrue(finalState.passphraseError)
        assertNull(finalState.progress)
        val credential = assertIs<BackupCredential.Passphrase>(json.importCalls.last().credential)
        assertEquals("hunter2", credential.bytes.decodeToString())
    }

    @Test
    fun `terminal import error surfaces as failure`() = runTest {
        json.inspectResult = JsonEncryption.ARK
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(emptyList())
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))
        viewModel.selectJson()
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Failed }

        assertEquals(ImportProgress.Failed(ImportError.NothingImported), finalState.progress)
    }

    @Test
    fun `Continue on a CSV file analyzes and advances to MapColumns`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val state = viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        assertEquals(2, state.columns.size)
        // selection seeded from the suggestion
        assertEquals(CsvColumnType.Title, state.columns[0].selectedType)
        assertEquals(CsvColumnType.Password, state.columns[1].selectedType)
        assertTrue(csv.importCalls.isEmpty()) // not imported yet
    }

    @Test
    fun `Continue on an unsupported file reports the format error`() = runTest {
        // The picker offers the wildcard MIME type, so a provider can hand back a .txt.
        val viewModel = viewModel(FakeBackupDestinationResolver(result = textDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/notes.txt"))
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(
            ImportProgress.Failed(ImportError.UnsupportedFormat),
            viewModel.state.value.progress,
        )
    }

    @Test
    fun `ChangeColumnType updates the selected type`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))
        advanceUntilIdle()

        assertEquals(CsvColumnType.Username, viewModel.state.value.columns[1].selectedType)
    }

    @Test
    fun `Continue with a duplicated type blocks import and reports duplicates`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        // Make both columns Title -> duplicate
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Title))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(setOf(CsvColumnType.Title), viewModel.state.value.duplicateTypes)
        assertEquals(ImportWizardStep.MapColumns, viewModel.state.value.step)
        assertTrue(csv.importCalls.isEmpty())
    }

    @Test
    fun `Continue with every column set to Ignore does not start import`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        // user sets every column to Ignore
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(0, null))
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, null))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        advanceUntilIdle()

        assertNull(viewModel.state.value.progress) // import never even began
        assertTrue(csv.importCalls.isEmpty())
        assertEquals(ImportWizardStep.MapColumns, viewModel.state.value.step)
    }

    @Test
    fun `Continue with a valid mapping imports using the edited mapping`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        // user reassigns col 1 from Password to Username
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Succeeded }

        assertIs<ImportProgress.Succeeded>(finalState.progress)
        val mapping = csv.importCalls.last().mapping
        assertEquals(0u, mapping.title)
        assertEquals(1u, mapping.username)
        assertNull(mapping.password)
    }

    @Test
    fun `a valid mapping advances to vault selection instead of importing`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val state = viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        assertNull(state.progress)
        assertTrue(csv.importCalls.isEmpty())
    }

    @Test
    fun `vault selection preselects the current vault context`() = runTest {
        val personal = testVault(name = "Personal")
        env.vaultRepo.seed(personal)
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(
            FakeBackupDestinationResolver(result = csvDestination()),
            contextRepo = FakeVaultContextRepository(VaultContext.ById(personal.id)),
        )
        viewModel.advanceToMapColumns()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val state = viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        assertEquals(personal.id, state.selectedVaultId)
        assertFalse(state.creatingNewVault)
    }

    @Test
    fun `vault selection falls back to a new vault named after the file`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val state = viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        assertTrue(state.creatingNewVault)
        assertNull(state.selectedVaultId)
        assertEquals("keygo", state.newVaultNameState.text.toString())
    }

    @Test
    fun `importing into an existing vault does not create one`() = runTest {
        val personal = testVault(name = "Personal")
        env.vaultRepo.seed(personal)
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        viewModel.onEvent(ImportWizardUiEvent.SelectVault(personal.id))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Succeeded }

        val succeeded = assertIs<ImportProgress.Succeeded>(finalState.progress)
        assertEquals(1, succeeded.summary.imported)
        assertEquals(0, succeeded.summary.vaultsCreated)
        assertEquals(1, env.loginRepo.getLoginsByVault(personal.id).size)
    }

    @Test
    fun `importing into a new vault creates it`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Succeeded }

        val succeeded = assertIs<ImportProgress.Succeeded>(finalState.progress)
        assertEquals(1, succeeded.summary.vaultsCreated)
        assertEquals(
            listOf("keygo"),
            env.vaultRepo.observeAllVaultMetadata().first().map { it.name },
        )
    }

    @Test
    fun `a new vault is created with the icon picked in the wizard`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        viewModel.onEvent(ImportWizardUiEvent.SelectNewVaultIcon(Vault.Icon.ShoppingCart))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.progress is ImportProgress.Succeeded }

        assertEquals(
            Vault.Icon.ShoppingCart,
            env.vaultRepo.observeAllVaultMetadata().first().single().icon,
        )
    }

    @Test
    fun `a new vault falls back to the default icon when none is picked`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        csv.importResult = CsvImportResult(
            backup = Backup(listOf(backupVault("CSV Import", listOf(login("Email"))))),
            report = ImportReport(imported = 1u, skipped = 0u),
        )
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.progress is ImportProgress.Succeeded }

        assertEquals(
            Vault.Icon.Default,
            env.vaultRepo.observeAllVaultMetadata().first().single().icon,
        )
    }

    @Test
    fun `Back from vault selection returns to the mapping with the mapping intact`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        viewModel.onEvent(ImportWizardUiEvent.Back)
        val state = viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        assertEquals(2, state.columns.size)
        assertEquals(CsvColumnType.Username, state.columns[1].selectedType)
    }

    @Test
    fun `re-entering vault selection after Back preserves a hand-picked vault`() = runTest {
        val personal = testVault(name = "Personal")
        env.vaultRepo.seed(personal)
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.advanceToMapColumns()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        // user picks the existing vault by hand, instead of the seeded new-vault default
        viewModel.onEvent(ImportWizardUiEvent.SelectVault(personal.id))
        viewModel.state.first { it.selectedVaultId == personal.id }

        // back to tweak a column mapping, then continue again
        viewModel.onEvent(ImportWizardUiEvent.Back)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val state = viewModel.state.first { it.step == ImportWizardStep.SelectVault }

        assertEquals(personal.id, state.selectedVaultId)
        assertFalse(state.creatingNewVault)
    }

    @Test
    fun `analyze failure surfaces as failure without routing to passphrase`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        val cause = BackupException.Csv("bad csv")
        csv.analyzeException = cause
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()

        viewModel.onEvent(ImportWizardUiEvent.Continue)
        val finalState = viewModel.state.first { it.progress is ImportProgress.Failed }

        assertEquals(ImportWizardStep.SelectFile, finalState.step)
        assertEquals(ImportProgress.Failed(ImportError.ParseFailed(cause)), finalState.progress)

        viewModel.onEvent(ImportWizardUiEvent.Back)
        val backState = viewModel.state.first { it.progress == null }

        assertNull(backState.progress)
        assertEquals(ImportWizardStep.SelectFile, backState.step)
    }

    @Test
    fun `Back from MapColumns returns to file selection`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        viewModel.onEvent(ImportWizardUiEvent.Back)
        advanceUntilIdle()

        assertEquals(ImportWizardStep.SelectFile, viewModel.state.value.step)
    }

    @Test
    fun `Back from passphrase step returns to file selection`() = runTest {
        json.inspectResult = JsonEncryption.PASSPHRASE
        fileStore.contents = """{"vaults":[]}"""
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))
        viewModel.selectJson()
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.ProvidePassphrase }

        viewModel.onEvent(ImportWizardUiEvent.Back)
        advanceUntilIdle()

        assertEquals(ImportWizardStep.SelectFile, viewModel.state.value.step)
    }

    @Test
    fun `seeding a CSV skips the file step and lands on the mapping`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))

        viewModel.seedFile(BackupDestinationUri("content://doc/keygo.csv"))
        val state = viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        assertTrue(state.fileChosenByHost)
        assertNull(state.progress)
        assertEquals(listOf(ImportWizardStep.MapColumns), state.steps)
    }

    @Test
    fun `seeding an ARK sealed JSON imports without asking anything`() = runTest {
        json.inspectResult = JsonEncryption.ARK
        fileStore.contents = """{"vaults":[]}"""
        json.importResult = Backup(listOf(backupVault("Imported", listOf(login("Email")))))
        val viewModel = viewModel(FakeBackupDestinationResolver(result = jsonDestination()))

        viewModel.seedFile(BackupDestinationUri("content://doc/keygo.json"))
        val state = viewModel.state.first { it.progress is ImportProgress.Succeeded }

        assertEquals(1, assertIs<ImportProgress.Succeeded>(state.progress).summary.imported)
    }

    @Test
    fun `seeding the same file again does not restart the import`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val uri = BackupDestinationUri("content://doc/keygo.csv")
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.seedFile(uri)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))

        viewModel.seedFile(uri)
        advanceUntilIdle()

        // A configuration change re-runs the seeding effect. The mapping in progress has to survive.
        assertEquals(ImportWizardStep.MapColumns, viewModel.state.value.step)
        assertEquals(CsvColumnType.Username, viewModel.state.value.columns[1].selectedType)
    }

    @Test
    fun `back from the first seeded step hands control to the host`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.seedFile(BackupDestinationUri("content://doc/keygo.csv"))
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        viewModel.onEvent(ImportWizardUiEvent.Back)

        assertEquals(ImportWizardEvent.Exit, viewModel.event.first())
    }

    @Test
    fun `the same file can be seeded again after handing control back`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val uri = BackupDestinationUri("content://doc/keygo.csv")
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.seedFile(uri)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }
        // Edit the mapping so a no-op second seed and a real one are distinguishable: a real one
        // re-analyzes the file and throws this edit away, a no-op leaves it exactly as it is.
        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))
        viewModel.onEvent(ImportWizardUiEvent.Back)
        viewModel.event.first()
        // Let state catch up to the reset exit() made. It still reports MapColumns until it does,
        // and the await below would match that stale value instead of the second seed's.
        advanceUntilIdle()

        viewModel.seedFile(uri)
        val state = viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        // Back hands control back, and exit() resets the step to SelectFile, so arriving at
        // MapColumns again already means a second seed ran. The analyzer count and the mapping
        // reverting to its freshly suggested value instead of the edit above pin down that it
        // re-read the file rather than restoring a step.
        assertEquals(2, csv.analyzeCalls.size)
        assertEquals(CsvColumnType.Password, state.columns[1].selectedType)
    }

    @Test
    fun `seeding a file KeyGo cannot read reports the format`() = runTest {
        val viewModel = viewModel(FakeBackupDestinationResolver(result = textDestination()))

        viewModel.seedFile(BackupDestinationUri("content://doc/notes.txt"))
        val state = viewModel.state.first { it.progress is ImportProgress.Failed }

        assertEquals(ImportProgress.Failed(ImportError.UnsupportedFormat), state.progress)
    }

    @Test
    fun `exiting after an unsupported file error resets the wizard to a fresh state`() = runTest {
        val viewModel = viewModel(FakeBackupDestinationResolver(result = textDestination()))
        viewModel.seedFile(BackupDestinationUri("content://doc/notes.txt"))
        viewModel.state.first { it.progress is ImportProgress.Failed }

        viewModel.onEvent(ImportWizardUiEvent.Back)
        viewModel.event.first()
        advanceUntilIdle()

        // This ViewModel is scoped to the host's back stack entry, so it outlives the visit. The
        // gap between handing control back and the host seeding a new file is exactly what a second
        // entry into the wizard would render if exit() left the dismissed error behind.
        val state = viewModel.state.value
        assertNull(state.progress)
        assertEquals(ImportWizardStep.SelectFile, state.step)
        assertFalse(state.fileChosenByHost)
        assertNull(state.backupDestination)
        assertNull(state.uri)
    }

    @Test
    fun `seeding a different file after backing out of a mapping does not carry over the old file's state`() =
        runTest {
            fileStore.contents = "name,secret\nEmail,s3cr3t\n"
            csv.analyzeResult = csvAnalysis()
            val resolver = FakeBackupDestinationResolver(result = csvDestination())
            val viewModel = viewModel(resolver)
            viewModel.seedFile(BackupDestinationUri("content://doc/keygo.csv"))
            viewModel.state.first { it.step == ImportWizardStep.MapColumns }

            viewModel.onEvent(ImportWizardUiEvent.Back)
            viewModel.event.first()
            advanceUntilIdle()

            // Same gap as above, but from a mapping rather than an error: the previous file's
            // column names are the tell if exit() left them behind.
            val handedBackState = viewModel.state.value
            assertEquals(ImportWizardStep.SelectFile, handedBackState.step)
            assertEquals(emptyList(), handedBackState.columns)
            assertFalse(handedBackState.fileChosenByHost)

            resolver.result = jsonDestination()
            json.inspectResult = JsonEncryption.ARK
            fileStore.contents = """{"vaults":[]}"""
            json.importResult = Backup(listOf(backupVault("Imported", listOf(login("Email")))))
            viewModel.seedFile(BackupDestinationUri("content://doc/keygo.json"))
            val finalState = viewModel.state.first { it.progress is ImportProgress.Succeeded }

            assertEquals(1, assertIs<ImportProgress.Succeeded>(finalState.progress).summary.imported)
            assertEquals(emptyList(), finalState.columns)
        }

    @Test
    fun `seedFile resolves the destination without a concurrent state write re-running it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val resolver = FakeBackupDestinationResolver(result = textDestination(), gate = gate)
        val viewModel = viewModel(resolver)

        viewModel.seedFile(BackupDestinationUri("content://doc/notes.txt"))
        // seedFile's coroutine has called resolve() and is now parked on the gate, mid-CAS-lambda.
        runCurrent()

        // A state write that lands while that lambda is still suspended: this is the same window
        // the passphraseState.clearText() collector writes into in the real flow. If resolve() were
        // still called from inside _state.update, the CAS retry this forces would call it again.
        env.vaultRepo.seed(testVault(name = "Personal"))
        runCurrent()

        gate.complete(Unit)
        viewModel.state.first { it.backupDestination != null }

        assertEquals(listOf(BackupDestinationUri("content://doc/notes.txt")), resolver.calls)
    }
}
