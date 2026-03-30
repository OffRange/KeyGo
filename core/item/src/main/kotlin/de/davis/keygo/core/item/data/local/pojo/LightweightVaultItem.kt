package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.generated.domain.model.VaultItemType

internal data class LightweightVaultItem(
    val id: Long,
    val name: String,
    val itemType: VaultItemType
)
