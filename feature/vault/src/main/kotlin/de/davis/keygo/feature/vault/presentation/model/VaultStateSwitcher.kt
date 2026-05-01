package de.davis.keygo.feature.vault.presentation.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultMetadata

sealed interface VaultStateSwitcher {
    data object Closed : VaultStateSwitcher
    data object Selection : VaultStateSwitcher
    data object Create : VaultStateSwitcher
    data class Edit(val vaultMetadata: VaultMetadata) : VaultStateSwitcher
    data class Move(val srcVaultId: VaultId) : VaultStateSwitcher
}
