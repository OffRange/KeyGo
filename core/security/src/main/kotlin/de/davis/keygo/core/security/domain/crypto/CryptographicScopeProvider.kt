package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.KeyWrapException

interface CryptographicScopeProvider {

    // TODO: support envelop for easier API - maybe even fetch envelop by itemId?
    suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R

    /**
     * Re-wraps an item's key from one vault to another without exposing the unwrapped item key
     * or touching the item's encrypted secrets. The destination AAD reuses the item id but is
     * bound to [destinationVault]'s id.
     *
     * Returns [Result.Failure] with the underlying [KeyWrapException] when any of the
     * unwrap/wrap steps fail (e.g. corrupted blob, wrong key, invalid AAD).
     */
    suspend fun rewrapItemKey(
        sourceVault: WrappedVaultKeyInformation,
        sourceItem: WrappedItemKeyInformation,
        destinationVault: WrappedVaultKeyInformation,
    ): Result<KeyInformation, KeyWrapException>
}
