package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

data class ActiveVaultKeyInformation(
    val keyInformation: KeyInformation,
    val vaultId: VaultId
)
