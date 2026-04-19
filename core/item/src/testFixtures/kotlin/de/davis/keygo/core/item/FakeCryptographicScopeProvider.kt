package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import kotlin.coroutines.CoroutineContext

class FakeCryptographicScopeProvider : CryptographicScopeProvider {

    var wrappedKeyInformation: KeyInformation = KeyInformation(
        byteArrayOf(),
        byteArrayOf(),
    )

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R = block(
        FakeCryptographicScope { wrappedKeyInformation }
    )
}

private class FakeCryptographicScope(
    private val wrapper: suspend () -> KeyInformation
) : CryptographicScope {
    override suspend fun ByteArray.encrypt(
        label: String,
        context: CoroutineContext
    ): CryptographicData =
        CryptographicData(data = this, iv = byteArrayOf())

    override suspend fun CryptographicData.decrypt(
        label: String,
        context: CoroutineContext
    ): ByteArray = data

    override suspend fun wrapCurrentItemKey(context: CoroutineContext): KeyInformation = wrapper()
}
