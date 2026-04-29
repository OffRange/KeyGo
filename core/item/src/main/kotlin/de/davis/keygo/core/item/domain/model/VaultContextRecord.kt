package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

data class VaultContextRecord(
    val context: VaultContext,
    val lastInteractedVaultId: VaultId,
)
