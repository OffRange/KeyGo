package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId

data class ItemKeyEnvelope(
    val vaultId: VaultId,
    val itemId: ItemId,

    val itemKeyInformation: KeyInformation,
    val vaultKeyInformation: KeyInformation,
)
