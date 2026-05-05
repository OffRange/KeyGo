package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password

internal fun Password.toDataDomainInfos(passwordId: ItemId): Set<DomainInfoEntity> =
    domainInfos.map { it.toData(passwordId) }.toSet()