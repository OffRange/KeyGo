package de.davis.keygo.feature.item.create.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultMetadata

@Stable
data class VaultsState(
    val vaults: List<VaultMetadata>,
    val selectedVaultId: VaultId,
)
