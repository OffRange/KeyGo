package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import de.davis.keygo.feature.vault.presentation.AllVaultsIcon

@Composable
internal fun ListItemState.selectedVaultIcon(): Painter = when (vaultContext) {
    VaultContext.NoSpecific -> AllVaultsIcon
    is VaultContext.ById -> rememberVectorPainter(vaults.first { it.vaultId == vaultContext.vaultId }.icon.toImageVector())
}
