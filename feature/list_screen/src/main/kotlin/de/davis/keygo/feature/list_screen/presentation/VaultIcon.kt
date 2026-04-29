package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState

internal val AllVaultsIcon
    @Composable
    get() = painterResource(de.davis.keygo.core.ui.R.drawable.ic_launcher_monochrome)

@Composable
internal fun ListItemState.selectedVaultIcon(): Painter = when (vaultContext) {
    VaultContext.NoSpecific -> AllVaultsIcon
    is VaultContext.ById -> rememberVectorPainter(vaults.first { it.vaultId == vaultContext.vaultId }.icon.toImageVector())
}
