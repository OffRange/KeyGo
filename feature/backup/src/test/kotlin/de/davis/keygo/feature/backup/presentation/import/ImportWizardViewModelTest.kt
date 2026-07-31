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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        website = null,
        passkeys = emptyList(),
    )

    private fun viewModel(
        resolver: FakeBackupDestinationResolver = FakeBackupDestinationResolver(),
        session: FakeSession = FakeSession(startOnConstruct = true),
        contextRepo: FakeVaultContextRepository = FakeVaultContextRepository(),
    ) = ImportWizardViewModel(
        resolver,
        ImportBackupUseCase(fileStore, json, csv, env.restorer, session),
        AnalyzeCsvUseCase(fileStore, csv),
        ObserveVaultsAndSelectionUseCase(env.vaultRepo, contextRepo, SortUseCase()),
    )

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
    fun `ChangeColumnType updates the selected type`() = runTest {
        fileStore.contents = "name,secret\nEmail,s3cr3t\n"
        csv.analyzeResult = csvAnalysis()
        val viewModel = viewModel(FakeBackupDestinationResolver(result = csvDestination()))
        viewModel.onFilePicked(BackupDestinationUri("content://doc/keygo.csv"))
        advanceUntilIdle()
        viewModel.onEvent(ImportWizardUiEvent.Continue)
        viewModel.state.first { it.step == ImportWizardStep.MapColumns }

        viewModel.onEvent(ImportWizardUiEvent.ChangeColumnType(1, CsvColumnType.Username))

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

        assertEquals(ImportWizardStep.SelectFile, viewModel.state.value.step)
    }
}
