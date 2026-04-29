package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import kotlin.time.Clock
import kotlin.time.Instant

data class Vault(
    val id: VaultId = newVaultId(),
    val name: String,
    val keyInformation: KeyInformation,
    val icon: Icon,
    val createdAt: Instant = Clock.System.now(),
) {
    constructor(
        name: String,
        wrappedVaultKey: ByteArray,
        vaultKeyNonce: ByteArray,
        icon: Icon,
        id: VaultId = newVaultId(),
    ) : this(
        id = id,
        name = name,
        keyInformation = KeyInformation(
            wrappedKey = wrappedVaultKey,
            keyNonce = vaultKeyNonce,
        ),
        icon = icon
    )

    enum class Icon {
        School,
        Work,
        MenuBook,
        Home,
        Person,
        Flight,
        ShoppingCart,
        AccountBalanceWallet,
        Favorite,
        Restaurant,
        FitnessCenter,
        SportsEsports,
        MusicNote,
        Star;

        companion object {
            val Default = Person
        }
    }
}
