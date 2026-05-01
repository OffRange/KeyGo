package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import de.davisalessandro.keygo.rust.ItemManagerInterface
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import javax.crypto.spec.SecretKeySpec

/**
 * Constructs the production [CryptographicScopeProvider] backed by the supplied fakes.
 *
 * Use when a test depends on the AAD-binding semantics — ciphertext bound to
 * (vaultId, itemId, label), item keys wrapped under the vault key. For tests
 * that only need a deterministic round-trip with no AAD enforcement,
 * [RecordingCryptographicScopeProvider] is simpler.
 */
@Suppress("FunctionName")
fun BindingCryptographicScopeProvider(
    session: Session,
    itemManager: ItemManagerInterface,
    keyWrapper: KeyWrapperInterface,
): CryptographicScopeProvider = CryptographicScopeProviderImpl(
    session = session,
    itemManager = itemManager,
    keyWrapper = keyWrapper,
)

class FixedSession(override val dek: AesKey) : Session {
    override fun startSession(dek: AesKey) = Unit
    override fun endSession() = Unit

    companion object {
        fun random(): FixedSession =
            FixedSession(AesKey(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")))
    }
}
