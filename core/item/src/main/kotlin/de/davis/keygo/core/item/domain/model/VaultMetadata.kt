package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId
import kotlin.time.Clock
import kotlin.time.Instant

data class VaultMetadata(
    val vaultId: VaultId,
    val name: String,
    val icon: Vault.Icon,
    val createdAt: Instant = Clock.System.now(),
    val count: Int = 0,
)
