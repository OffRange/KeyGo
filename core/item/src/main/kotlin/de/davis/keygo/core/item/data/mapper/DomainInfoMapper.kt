package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.model.Login

internal fun Login.toDomainInfoEntities(): Set<DomainInfoEntity> =
    domainInfos.map { it.toData(this.id) }.toSet()