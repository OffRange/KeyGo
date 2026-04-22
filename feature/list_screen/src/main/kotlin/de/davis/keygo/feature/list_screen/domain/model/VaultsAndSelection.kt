package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.VaultMetadata

data class VaultsAndSelection(
    val vaults: List<VaultMetadata>,
    val selection: SelectedVault,
)
