package de.davis.keygo.feature.backup.presentation.import.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportTarget

@Stable
internal data class ImportWizardUiState(
    val passphraseState: TextFieldState = TextFieldState(),
    val newVaultNameState: TextFieldState = TextFieldState(),
    val backupDestination: BackupDestination? = null,
    val uri: BackupDestinationUri? = null,
    val step: ImportWizardStep = ImportWizardStep.SelectFile,
    /** True when the file was picked before the wizard opened, for example by onboarding. */
    val fileChosenByHost: Boolean = false,
    val passphraseValid: Boolean = false,
    val passphraseError: Boolean = false,
    val progress: ImportProgress? = null,
    val columns: List<ColumnMappingRow> = emptyList(),
    val duplicateTypes: Set<CsvColumnType> = emptySet(),
    val vaults: List<VaultMetadata> = emptyList(),
    val selectedVaultId: VaultId? = null,
    val creatingNewVault: Boolean = false,
    val newVaultNameValid: Boolean = false,
    val newVaultIcon: Vault.Icon = Vault.Icon.Default,
    /** The vault the user is currently working in; seeds the destination choice. */
    val contextVaultId: VaultId? = null,
) {
    val format: FileFormat?
        get() = FileFormat.fromFileName(backupDestination?.fileName)

    val steps: List<ImportWizardStep>
        get() = importStepsFor(step, includeFileStep = !fileChosenByHost)

    val suggestedVaultName: String
        get() = backupDestination?.fileName?.substringBeforeLast('.').orEmpty()

    val canContinue: Boolean
        get() = when (step) {
            ImportWizardStep.SelectFile -> backupDestination != null
            ImportWizardStep.MapColumns -> columns.any { it.selectedType != null }
            ImportWizardStep.SelectVault ->
                if (creatingNewVault) newVaultNameValid else selectedVaultId != null

            ImportWizardStep.ProvidePassphrase -> passphraseValid
        }

    val showContinueButton: Boolean
        get() = progress == null

    val backEnabled: Boolean
        get() = progress == null || progress is ImportProgress.Failed

    fun resolveTarget(newVaultName: String): ImportTarget? =
        if (creatingNewVault) newVaultName.trim().takeIf(String::isNotBlank)
            ?.let { ImportTarget.New(it, newVaultIcon) }
        else selectedVaultId?.let(ImportTarget::Existing)
}
