package de.davis.keygo.autofill.domain

import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem

internal fun LiteVaultItem.subtitle() = when (this) {
    is LitePassword -> username ?: "----"
}