package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultContext

internal fun VaultId?.toDomain(): VaultContext = this?.let {
    VaultContext.ById(it)
} ?: VaultContext.NoSpecific