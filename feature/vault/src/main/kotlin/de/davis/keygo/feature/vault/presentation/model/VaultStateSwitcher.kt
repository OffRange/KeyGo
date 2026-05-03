package de.davis.keygo.feature.vault.presentation.model

import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface VaultStateSwitcher {
    data object Selection : VaultStateSwitcher
    data object Create : VaultStateSwitcher
    data object Edit : VaultStateSwitcher
    data class Move(val srcVaultId: VaultId) : VaultStateSwitcher
}
