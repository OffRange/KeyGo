package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag

data class ItemAttributes(
    val passwordScoreByItem: Map<ItemId, PasswordScore> = emptyMap(),
    val tagsByItem: Map<ItemId, Set<Tag>> = emptyMap(),
    val credentialsByItem: Map<ItemId, Set<CredentialType>> = emptyMap(),
) {
    companion object {
        val None = ItemAttributes()
    }
}
