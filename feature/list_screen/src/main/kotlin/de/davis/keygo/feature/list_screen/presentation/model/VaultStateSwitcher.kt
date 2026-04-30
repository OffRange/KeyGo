package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultMetadata

internal sealed interface VaultStateSwitcher {
    data object Closed : VaultStateSwitcher
    data object Selection : VaultStateSwitcher
    data object Create : VaultStateSwitcher
    data class Edit(val vaultMetadata: VaultMetadata) : VaultStateSwitcher
    data class Move(val srcVaultId: VaultId) : VaultStateSwitcher
}
