package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation

interface CryptographicScopeProvider {

    suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R

    /**
     * Re-wraps an item's key from one vault to another without exposing the unwrapped item key
     * or touching the item's encrypted secrets. The destination AAD reuses the item id but is
     * bound to [destinationVault]'s id.
     */
    suspend fun rewrapItemKey(
        sourceVault: WrappedVaultKeyInformation,
        sourceItem: WrappedItemKeyInformation,
        destinationVault: WrappedVaultKeyInformation,
    ): KeyInformation
}
