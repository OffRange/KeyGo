package de.davis.keygo.core.security.domain.crypto.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davisalessandro.keygo.rust.ItemAad


data class WrappedItemKeyInformation(
    val itemAad: ItemAad,
    val wrappedItemKey: KeyInformation? = null,
)

data class WrappedVaultKeyInformation(
    val wrappedVaultKey: KeyInformation,
    val vaultId: VaultId
)
