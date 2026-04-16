package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo

internal fun DomainInfo.toData(passwordId: ItemId) = DomainInfoEntity(
    passwordId = passwordId,
    value = value,
    eTLD1 = eTLD1,
)

internal fun DomainInfoEntity.toDomain() = DomainInfo(
    passwordId = passwordId,
    value = value,
    eTLD1 = eTLD1,
)