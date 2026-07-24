package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import de.davis.keygo.feature.backup.presentation.import.model.toMappingRows
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import de.davisalessandro.keygo.rust.ColumnMapping
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ImportWizardViewModel(
    private val backupDestinationResolver: BackupDestinationResolver,
    private val importBackup: ImportBackupUseCase,
    private val analyzeCsv: AnalyzeCsvUseCase,
    private val observeVaultsAndSelection: ObserveVaultsAndSelectionUseCase,
) : ViewModel() {

    private val passphraseState = TextFieldState()
    private val newVaultNameState = TextFieldState()

    private val _state = MutableStateFlow(
        ImportWizardUiState(
            passphraseState = passphraseState,
            newVaultNameState = newVaultNameState,
        ),
    )
    val state = _state.asStateFlow()

    private val _event = Channel<ImportWizardEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var importJob: Job? = null
    private var analysisJob: Job? = null
    private var vaultStepSeeded = false

    init {
        snapshotFlow { passphraseState.text.toString() }
            .onEach { text -> _state.update { it.copy(passphraseValid = text.isNotBlank()) } }
            .launchIn(viewModelScope)

        snapshotFlow { newVaultNameState.text.toString() }
            .onEach { text -> _state.update { it.copy(newVaultNameValid = text.isNotBlank()) } }
            .launchIn(viewModelScope)

        observeVaultsAndSelection()
            .onEach { (vaults, selection) ->
                _state.update {
                    it.copy(vaults = vaults, contextVaultId = selection.getIdOrNull())
                }
            }
            .launchIn(viewModelScope)
    }

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

    private fun onContinue() = when (_state.value.step) {
        ImportWizardStep.SelectFile -> onSelectFileContinue()
        ImportWizardStep.MapColumns -> validateMapping()
        ImportWizardStep.SelectVault -> startTargetedImport()
        ImportWizardStep.ProvidePassphrase -> startImport(passphrase = passphraseState.text.toString())
    }

    private fun onSelectFileContinue() = when (_state.value.format) {
        FileFormat.CSV -> runAnalysis()
        FileFormat.JSON -> startImport(passphrase = null)
        null -> Unit
    }

    private fun runAnalysis() {
        val uri = _state.value.uri ?: return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            analyzeCsv(uri).fold(
                onSuccess = ::onAnalyzed,
                onFailure = { error -> _state.update { it.copy(progress = ImportProgress.Failed(error)) } },
            )
        }
    }

    private fun onAnalyzed(analysis: CsvColumnAnalysis) = _state.update {
        it.copy(
            columns = analysis.toMappingRows(),
            step = ImportWizardStep.MapColumns,
            duplicateTypes = emptySet(),
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
     * Seeds the destination on entry rather than in [ImportWizardUiState]'s initialiser: the file
     * name is only known once a file has been picked, and seeding on every state update would
     * overwrite a name the user has since typed. Falling back to a new vault when the context is
     * [de.davis.keygo.core.item.domain.model.VaultContext.NoSpecific] matters — that is the "all
     * vaults" view, which is not somewhere an import can land.
     *
     * Seeding itself only ever happens once per file, guarded by [vaultStepSeeded]: re-entering
     * this step via Back/Continue must not re-seed, or the user's own pick (or typed name) would be
     * silently discarded right before an irreversible bulk write. [onFilePicked] resets the flag so
     * a second import in the same session re-seeds correctly.
     */
    private fun enterSelectVault() {
        if (vaultStepSeeded) {
            _state.update { it.copy(step = ImportWizardStep.SelectVault) }
            return
        }
        vaultStepSeeded = true

        val current = _state.value
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
            csvMapping = current.columns.associate { it.index to it.selectedType }.toColumnMapping(),
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

    private fun back() = _state.update {
        when {
            it.progress is ImportProgress.Failed -> it.copy(
                progress = null,
                step = ImportWizardStep.SelectFile,
                passphraseError = false,
            )

            it.step == ImportWizardStep.SelectVault -> it.copy(step = ImportWizardStep.MapColumns)

            it.step == ImportWizardStep.MapColumns -> it.copy(
                step = ImportWizardStep.SelectFile,
                columns = emptyList(),
                duplicateTypes = emptySet(),
            )

            it.step == ImportWizardStep.ProvidePassphrase ->
                it.copy(step = ImportWizardStep.SelectFile, passphraseError = false)

            else -> it
        }
    }
}
