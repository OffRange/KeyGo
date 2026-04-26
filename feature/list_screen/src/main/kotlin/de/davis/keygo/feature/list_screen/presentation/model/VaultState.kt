package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.domain.model.VaultCreationError

@Stable
internal data class VaultState(
    val vaults: List<VaultMetadata> = emptyList(),
    val selectedVault: SelectedVault = SelectedVault.All,
    val showSelection: Boolean = false,
    val showCreationDialog: Boolean = false,
    val toolModeActive: Boolean = false,
    val error: VaultCreationError? = null,
)
