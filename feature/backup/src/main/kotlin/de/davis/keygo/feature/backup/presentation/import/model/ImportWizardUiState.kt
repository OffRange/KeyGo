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
    val format: FileFormat? = backupDestination?.fileName?.let { name ->
        when {
            name.endsWith(".${FileFormat.JSON.extension}", ignoreCase = true) -> FileFormat.JSON
            name.endsWith(".${FileFormat.CSV.extension}", ignoreCase = true) -> FileFormat.CSV
            else -> null
        }
    }

    val steps: List<ImportWizardStep> = importStepsFor(step)

    /** `passwords.csv` -> `passwords`. Distinct per import, unlike the parser's `CSV Import`. */
    val suggestedVaultName: String =
        backupDestination?.fileName?.substringBeforeLast('.').orEmpty()

    val canContinue: Boolean = when (step) {
        ImportWizardStep.SelectFile -> backupDestination != null
        ImportWizardStep.MapColumns -> columns.any { it.selectedType != null }
        ImportWizardStep.SelectVault ->
            if (creatingNewVault) newVaultNameValid else selectedVaultId != null

        ImportWizardStep.ProvidePassphrase -> passphraseValid
    }

    val showContinueButton: Boolean = progress == null

    /**
     * A function rather than a computed property: the new-vault name lives in a [TextFieldState],
     * and a `val` would capture whatever it held when this state object was built rather than what
     * the user has typed since.
     */
    fun resolveTarget(newVaultName: String): ImportTarget? =
        if (creatingNewVault) newVaultName.trim().takeIf(String::isNotBlank)
            ?.let { ImportTarget.New(it, newVaultIcon) }
        else selectedVaultId?.let(ImportTarget::Existing)
}
