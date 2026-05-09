package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo

internal fun DomainInfo.toData(loginId: ItemId) = DomainInfoEntity(
    loginId = loginId,
    value = value,
    eTLD1 = eTLD1,
)

internal fun DomainInfoEntity.toDomain() = DomainInfo(
    loginId = loginId,
    value = value,
    eTLD1 = eTLD1,
)