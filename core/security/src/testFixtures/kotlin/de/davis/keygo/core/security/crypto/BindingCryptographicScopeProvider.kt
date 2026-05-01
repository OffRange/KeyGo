package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davisalessandro.keygo.rust.ItemManagerInterface
import de.davisalessandro.keygo.rust.KeyWrapperInterface

/**
 * Constructs the production [CryptographicScopeProvider] backed by the supplied fakes.
 *
 * Use when a test depends on the AAD-binding semantics — ciphertext bound to
 * (vaultId, itemId, label), item keys wrapped under the vault key. For tests
 * that only need a deterministic round-trip with no AAD enforcement,
 * [FakeCryptographicScopeProvider] is simpler.
 */
@Suppress("TestFunctionName")
fun BindingCryptographicScopeProvider(
    session: Session,
    itemManager: ItemManagerInterface,
    keyWrapper: KeyWrapperInterface,
): CryptographicScopeProvider = CryptographicScopeProviderImpl(
    session = session,
    itemManager = itemManager,
    keyWrapper = keyWrapper,
)
