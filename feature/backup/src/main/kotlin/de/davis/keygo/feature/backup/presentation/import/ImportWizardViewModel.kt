package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.getIdOrNull
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.mapper.toColumnMapping
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.CsvColumnAnalysis
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportRequest
import de.davis.keygo.feature.backup.domain.model.ImportTarget
import de.davis.keygo.feature.backup.domain.usecase.AnalyzeCsvUseCase
import de.davis.keygo.feature.backup.domain.usecase.ImportBackupUseCase
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardEvent
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardStep
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.import.model.ImportWizardUiState
import de.davis.keygo.feature.backup.presentation.import.model.previousStep
import de.davis.keygo.feature.backup.presentation.import.model.toMappingRows
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import de.davisalessandro.keygo.rust.ColumnMapping
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ImportWizardViewModel(
    private val backupDestinationResolver: BackupDestinationResolver,
    private val importBackup: ImportBackupUseCase,
    private val analyzeCsv: AnalyzeCsvUseCase,
    observeVaultsAndSelection: ObserveVaultsAndSelectionUseCase,
) : ViewModel() {

    private val _event = Channel<ImportWizardEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var importJob: Job? = null
    private var analysisJob: Job? = null
    private var seedJob: Job? = null
    private var vaultStepSeeded = false
    private var seededUri: BackupDestinationUri? = null


    private val passphraseState = TextFieldState()
    private val newVaultNameState = TextFieldState()

    private val _state = MutableStateFlow(
        ImportWizardUiState(
            passphraseState = passphraseState,
            newVaultNameState = newVaultNameState,
        ),
    )

    val state = combine(
        _state,
        snapshotFlow { passphraseState.text.toString() },
        snapshotFlow { newVaultNameState.text.toString() },
        observeVaultsAndSelection()
    ) { baseState, passphrase, vaultName, vaultData ->
        val (vaults, selection) = vaultData

        baseState.copy(
            passphraseValid = passphrase.isNotBlank(),
            newVaultNameValid = vaultName.isNotBlank(),
            vaults = vaults,
            contextVaultId = selection.getIdOrNull()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value
    )

    fun onEvent(event: ImportWizardUiEvent) {
        when (event) {
            ImportWizardUiEvent.ChooseFile -> _event.trySend(ImportWizardEvent.PickFile)
            ImportWizardUiEvent.Continue -> onContinue()
            ImportWizardUiEvent.Back -> back()
            is ImportWizardUiEvent.ChangeColumnType -> _state.update { state ->
                state.copy(
                    columns = state.columns.map { column ->
                        if (column.index == event.columnIndex) column.copy(selectedType = event.type)
                        else column
                    },
                    duplicateTypes = emptySet(),
                )
            }

            is ImportWizardUiEvent.SelectVault -> _state.update {
                it.copy(selectedVaultId = event.vaultId, creatingNewVault = false)
            }

            ImportWizardUiEvent.CreateNewVault -> _state.update {
                it.copy(creatingNewVault = true)
            }

            is ImportWizardUiEvent.SelectNewVaultIcon -> _state.update {
                it.copy(newVaultIcon = event.icon)
            }
        }
    }

    fun onFilePicked(uri: BackupDestinationUri?) {
        if (uri == null) return

        vaultStepSeeded = false
        viewModelScope.launch {
            val destination = backupDestinationResolver.resolve(uri)
            _state.update {
                it.copy(backupDestination = destination, uri = uri)
            }
        }
    }

    /**
     * Enters the wizard on a file the host picked, so that choosing a file stays one action in one
     * place. The file step is skipped and the lane resumes at the first question the wizard still
     * has, which is the column mapping for a CSV and nothing at all for a JSON that imports cleanly.
     *
     * Idempotent per file. The screen seeds from a [androidx.compose.runtime.LaunchedEffect], which
     * restarts on a configuration change, and re-running the import there would throw away the
     * mapping the user was in the middle of.
     */
    fun seedFile(uri: BackupDestinationUri) {
        if (seededUri == uri) return
        seededUri = uri

        cancelInFlightWork()
        _state.update {
            it.cleared().copy(
                uri = uri,
                fileChosenByHost = true,
                // Held until the lane below decides where the user lands, so the wizard never
                // flashes the file step its host already owns.
                progress = ImportProgress.Reading,
            )
        }

        seedJob = viewModelScope.launch {
            val destination = backupDestinationResolver.resolve(uri)
            _state.update { it.copy(backupDestination = destination) }
            onContinue()
        }
    }

    private fun onContinue() = when (_state.value.step) {
        ImportWizardStep.SelectFile -> onSelectFileContinue()
        ImportWizardStep.MapColumns -> validateMapping()
        ImportWizardStep.SelectVault -> startTargetedImport()
        ImportWizardStep.ProvidePassphrase -> startImport(passphrase = passphraseState.text.toString())
    }

    private fun onSelectFileContinue() = when (_state.value.format) {
        FileFormat.CSV -> runAnalysis()
        FileFormat.JSON -> startImport(passphrase = null)

        // Reachable because the picker has to offer the wildcard type: providers routinely report
        // the wrong MIME type for a .csv, so the filter cannot be tight enough to keep a .txt out.
        null -> _state.update {
            it.copy(progress = ImportProgress.Failed(ImportError.UnsupportedFormat))
        }
    }

    private fun runAnalysis() {
        val uri = _state.value.uri ?: return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            analyzeCsv(uri).fold(
                onSuccess = ::onAnalyzed,
                onFailure = { error ->
                    _state.update {
                        it.copy(progress = ImportProgress.Failed(error))
                    }
                },
            )
        }
    }

    private fun onAnalyzed(analysis: CsvColumnAnalysis) = _state.update {
        it.copy(
            columns = analysis.toMappingRows(),
            step = ImportWizardStep.MapColumns,
            duplicateTypes = emptySet(),
            progress = null,
        )
    }

    private fun validateMapping() {
        val columns = _state.value.columns
        val assigned = columns.mapNotNull { it.selectedType }
        if (assigned.isEmpty()) return

        val duplicates = assigned.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) _state.update { it.copy(duplicateTypes = duplicates) }
        else enterSelectVault()
    }

    /**
     * Seeding happens once per file, guarded by [vaultStepSeeded]: re-entering this step via
     * Back/Continue must not re-seed, or the user's own pick (or typed name) would be silently
     * discarded right before an irreversible bulk write. [onFilePicked] resets the flag so a second
     * import in the same session re-seeds correctly.
     */
    private fun enterSelectVault() {
        if (vaultStepSeeded) {
            _state.update { it.copy(step = ImportWizardStep.SelectVault) }
            return
        }
        vaultStepSeeded = true

        // state, not _state: the vault list and the vault context are folded in by the flow above
        // and never written back, so _state has neither. This runs from a Continue tap, so the
        // screen is collecting and the values are present.
        val current = state.value
        val contextVault = current.contextVaultId
            ?.takeIf { id -> current.vaults.any { it.vaultId == id } }

        if (contextVault == null)
            newVaultNameState.setTextAndPlaceCursorAtEnd(current.suggestedVaultName)

        _state.update {
            it.copy(
                step = ImportWizardStep.SelectVault,
                selectedVaultId = contextVault,
                creatingNewVault = contextVault == null,
            )
        }
    }

    private fun startTargetedImport() {
        val current = _state.value
        val target = current.resolveTarget(newVaultNameState.text.toString()) ?: return

        startImport(
            passphrase = null,
            csvMapping = current.columns.associate { it.index to it.selectedType }
                .toColumnMapping(),
            target = target,
        )
    }

    private fun startImport(
        passphrase: String?,
        csvMapping: ColumnMapping? = null,
        target: ImportTarget? = null,
    ) {
        val current = _state.value
        val uri = current.uri ?: return
        val format = current.format ?: return

        importJob?.cancel()
        importJob = viewModelScope.launch {
            importBackup(
                ImportRequest(
                    uri = uri,
                    format = format,
                    passphrase = passphrase,
                    csvMapping = csvMapping,
                    target = target,
                ),
            ).collect(::onProgress)
        }
    }

    private fun onProgress(progress: ImportProgress) {
        if (progress is ImportProgress.Failed) handleFailure(progress.error)
        else _state.update { it.copy(progress = progress, passphraseError = false) }
    }

    private fun handleFailure(error: ImportError) = when (error) {
        ImportError.PassphraseRequired -> _state.update {
            it.copy(step = ImportWizardStep.ProvidePassphrase, progress = null)
        }

        ImportError.WrongCredential -> _state.update {
            it.copy(
                step = ImportWizardStep.ProvidePassphrase,
                progress = null,
                passphraseError = true,
            )
        }

        else -> _state.update { it.copy(progress = ImportProgress.Failed(error)) }
    }

    private fun back() {
        val current = _state.value

        // A failure restarts from the file. When the host picked it there is no file step to
        // restart from, so the wizard hands control back instead.
        if (current.progress is ImportProgress.Failed) {
            if (current.fileChosenByHost) return exit()

            return _state.update {
                it.copy(
                    progress = null,
                    step = ImportWizardStep.SelectFile,
                    passphraseError = false,
                )
            }
        }

        val previous = current.step.previousStep(current.fileChosenByHost)
        if (previous == null) {
            if (current.fileChosenByHost) exit()
            return
        }

        // duplicateTypes can only be set on MapColumns (validateMapping refuses to advance while
        // duplicates remain) and passphraseError only on ProvidePassphrase, so clearing both
        // unconditionally is the same as clearing them per step. Only the columns are conditional.
        _state.update {
            it.copy(
                step = previous,
                columns = if (current.step == ImportWizardStep.MapColumns) emptyList()
                else it.columns,
                duplicateTypes = emptySet(),
                passphraseError = false,
            )
        }
    }

    /**
     * Hands control back to whoever opened the wizard, stops any work still in flight, and resets
     * the UI state to what a fresh wizard looks like.
     *
     * The reset matters because this ViewModel is scoped to the host's back stack entry, so it
     * outlives the visit: without it, the previous file's screen (its error, or its column mapping)
     * would render again for the gap between handing control back and the host seeding a new file.
     */
    private fun exit() {
        cancelInFlightWork()
        seededUri = null
        _state.update { it.cleared() }
        _event.trySend(ImportWizardEvent.Exit)
    }

    private fun cancelInFlightWork() {
        importJob?.cancel()
        analysisJob?.cancel()
        seedJob?.cancel()
        vaultStepSeeded = false
        passphraseState.clearText()
        newVaultNameState.clearText()
    }

    /**
     * What a fresh wizard looks like. `vaults` and `contextVaultId` are owned by the observe flow
     * rather than by a visit, so they survive; the text field states are held by this ViewModel and
     * are cleared by [cancelInFlightWork].
     */
    private fun ImportWizardUiState.cleared() = copy(
        step = ImportWizardStep.SelectFile,
        fileChosenByHost = false,
        uri = null,
        backupDestination = null,
        progress = null,
        columns = emptyList(),
        duplicateTypes = emptySet(),
        passphraseError = false,
        creatingNewVault = false,
        selectedVaultId = null,
        newVaultIcon = Vault.Icon.Default,
    )
}
