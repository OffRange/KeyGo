package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password

internal data class PasswordScoreEntry(
    val id: ItemId,
    val score: Password.Score,
)
