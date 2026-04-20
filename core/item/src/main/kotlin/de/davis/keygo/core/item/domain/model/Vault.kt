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
        id: VaultId = newVaultId(),
    ) : this(
        id = id,
        name = name,
        keyInformation = KeyInformation(
            wrappedKey = wrappedVaultKey,
            keyNonce = vaultKeyNonce,
        )
    )

    enum class Icon {
        School,
        Work,
        MenuBook,
        Home,
        Flight,
        ShoppingCart,
        AccountBalanceWallet,
        Favorite,
        Restaurant,
        FitnessCenter,
        SportsEsports,
        MusicNote,
        Star
    }
}
