package de.davis.keygo.feature.vault.domain.model

import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultMetadata

data class VaultsAndSelection(
    val vaults: List<VaultMetadata>,
    val selection: VaultContext,
)
