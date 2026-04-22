package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface SelectedVault {
    data object All : SelectedVault
    data class Id(val vaultId: VaultId) : SelectedVault
}
