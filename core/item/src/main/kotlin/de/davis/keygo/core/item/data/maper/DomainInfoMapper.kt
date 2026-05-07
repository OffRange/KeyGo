package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login

internal fun Login.toDataDomainInfos(loginId: ItemId): Set<DomainInfoEntity> =
    domainInfos.map { it.toData(loginId) }.toSet()