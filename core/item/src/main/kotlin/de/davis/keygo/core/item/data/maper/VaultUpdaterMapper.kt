package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.domain.model.VaultUpdater

internal fun VaultUpdater.toData() = de.davis.keygo.core.item.data.local.pojo.VaultUpdater(
    id = id,
    name = name,
    icon = icon,
)