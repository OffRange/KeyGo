package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davisalessandro.keygo.rust.ItemAad

fun Item.itemAad(): ItemAad = ItemAad(itemId = id, vaultId = vaultId)

fun Item.wrappedItemKeyInformation(): WrappedItemKeyInformation = WrappedItemKeyInformation(
    itemAad = itemAad(),
    wrappedItemKey = keyInformation
)