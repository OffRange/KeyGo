package de.davis.keygo.core.item.data.local.pojo

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login

internal data class PasswordScoreProjection(
    val id: ItemId,
    val score: Login.Score,
)
