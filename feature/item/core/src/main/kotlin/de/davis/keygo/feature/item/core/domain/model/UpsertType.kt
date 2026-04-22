package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface UpsertType {
    data class Create(val vaultId: VaultId) : UpsertType
    data class Update(val id: ItemId) : UpsertType
}