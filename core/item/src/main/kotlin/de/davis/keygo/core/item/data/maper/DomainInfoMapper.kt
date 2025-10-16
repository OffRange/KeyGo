package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password

internal fun Password.toDataDomainInfos(passwordId: ItemId = id): Set<DomainInfoEntity> =
    domainInfos.map {
        it.toData(passwordId = passwordId)
    }.toSet()

internal fun DomainInfo.toData(passwordId: Long): DomainInfoEntity = DomainInfoEntity(
    passwordId = passwordId,
    value = value,
    eTLD1 = eTLD1,
)