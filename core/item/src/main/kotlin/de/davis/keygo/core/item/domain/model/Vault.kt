package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId

data class Vault(
    val id: VaultId = newVaultId(),
    val name: String,
    val keyInformation: KeyInformation,
) {
    constructor(
        name: String,
        wrappedVaultKey: ByteArray,
        vaultKeyNonce: ByteArray,
    ) : this(
        name = name,
        keyInformation = KeyInformation(
            wrappedKey = wrappedVaultKey,
            keyNonce = vaultKeyNonce,
        )
    )
}
