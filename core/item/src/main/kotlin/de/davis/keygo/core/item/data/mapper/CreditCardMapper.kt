package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.data.local.pojo.CreditCardProjection
import de.davis.keygo.core.item.domain.model.CreditCard

internal fun CreditCard.toCreditCardEntity() = CreditCardEntity(
    id = id,
    holder = holder,
    cardNumber = cardNumber?.payload,
    cvv = cvv?.payload,
    expirationDate = expirationDate
)

internal fun CreditCardProjection.toDomain() = CreditCard(
    id = item.itemEntity.id,
    vaultId = item.itemEntity.vaultId,
    name = item.itemEntity.name,
    keyInformation = item.itemEntity.keyInformation.toDomain(),
    timestamp = item.itemEntity.timestamp.toDomain(),
    tags = item.tags.map(TagEntity::toDomain).toSet(),
    note = item.itemEntity.note,
    pinned = item.itemEntity.pinned,

    holder = creditCardEntity.holder,
    cardNumber = creditCardEntity.cardNumber?.let { CreditCard.CardNumber(it) },
    cvv = creditCardEntity.cvv?.let { CreditCard.CVV(it) },
    expirationDate = creditCardEntity.expirationDate,
)
