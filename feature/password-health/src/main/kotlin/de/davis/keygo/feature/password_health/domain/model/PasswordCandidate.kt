package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore

class PasswordCandidate(
    val id: ItemId,
    val title: String,
    val username: String?,
    val score: PasswordScore,
    val password: CharArray,
)
