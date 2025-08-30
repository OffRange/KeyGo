package de.davis.keygo.autofill.domain

import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult

internal fun VaultItem.subtitle() = when (this) {
    is Password -> this.username ?: "----"
    is VaultItem.Basic,
    is VaultSearchResult -> "<unsupported>"
}