package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.Embedded
import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId

internal data class ItemWrappedKeyRecord(
    val vaultId: VaultId,
    val itemId: ItemId,

    @Embedded(prefix = "item_")
    val itemKeyInformation: KeyInformation,
    @Embedded(prefix = "vault_")
    val vaultKeyInformation: KeyInformation,
)
