package de.davis.keygo.feature.backup.presentation.import.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.feature.backup.domain.model.CsvColumnType

internal sealed interface ImportWizardUiEvent {
    data object Back : ImportWizardUiEvent
    data object Continue : ImportWizardUiEvent
    data object ChooseFile : ImportWizardUiEvent
    data class ChangeColumnType(val columnIndex: Int, val type: CsvColumnType?) : ImportWizardUiEvent
    data class SelectVault(val vaultId: VaultId) : ImportWizardUiEvent
    data object CreateNewVault : ImportWizardUiEvent
    data class SelectNewVaultIcon(val icon: Vault.Icon) : ImportWizardUiEvent
}
