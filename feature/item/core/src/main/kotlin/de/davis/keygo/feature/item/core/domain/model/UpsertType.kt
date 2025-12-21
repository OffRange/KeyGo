package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

sealed interface UpsertType {
    data object Create : UpsertType
    data class Update(val vaultItemId: ItemId) : UpsertType
}