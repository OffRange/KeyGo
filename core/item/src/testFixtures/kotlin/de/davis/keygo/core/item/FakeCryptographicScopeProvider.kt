package de.davis.keygo.core.item

import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlin.coroutines.CoroutineContext

/**
 * Passthrough [CryptographicScopeProvider] for tests. Encrypt/decrypt are identity operations —
 * raw bytes are stored as-is, so encrypted values remain inspectable in assertions without
 * requiring a real Android Keystore.
 */
class FakeCryptographicScopeProvider : CryptographicScopeProvider {
    override suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R =
        block(FakeCryptographicScope)
}

private object FakeCryptographicScope : CryptographicScope {
    override suspend fun ByteArray.encrypt(context: CoroutineContext): CryptographicData =
        CryptographicData(data = this, iv = byteArrayOf())

    override suspend fun CryptographicData.decrypt(context: CoroutineContext): ByteArray = data
}
