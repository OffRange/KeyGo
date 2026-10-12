package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.rust.FakeItemManager
import de.davis.keygo.rust.FakeKeyWrapper
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.KeyWrapException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptographicScopeProviderImplTest {

    private val session = FakeSession()
    private val provider = CryptographicScopeProviderImpl(
        session = session,
        itemRepository = FakeItemRepository(),
        itemManager = FakeItemManager(),
        keyWrapper = FakeKeyWrapper(),
    )

    private fun wrappedVaultKeyInformation(vaultId: java.util.UUID) = WrappedVaultKeyInformation(
        wrappedVaultKey = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
        vaultId = vaultId,
    )

    private fun wrappedItemKeyInformation(itemId: java.util.UUID, vaultId: java.util.UUID) =
        WrappedItemKeyInformation(
            itemAad = ItemAad(itemId = itemId, vaultId = vaultId),
            wrappedItemKey = null,
        )

    @Test
    fun `itemScope returns NoActiveSession instead of throwing when the session was never started`() =
        runTest {
            val vaultId = newVaultId()
            val itemId = newItemId()

            val result = provider.itemScope(
                wrappedVaultKeyInformation = wrappedVaultKeyInformation(vaultId),
                wrappedItemKeyInformation = wrappedItemKeyInformation(itemId, vaultId),
            ) { }

            assertTrue(result.isFailure())
            val failure = assertIs<Result.Failure<*, *>>(result)
            assertIs<CryptoScopeError.NoActiveSession>(failure.error)
        }

    @Test
    fun `itemScope returns NoActiveSession instead of throwing after the session has ended`() =
        runTest {
            val vaultId = newVaultId()
            val itemId = newItemId()

            session.unlockWithArk(ByteArray(32) { it.toByte() })
            session.endSession()

            val result = provider.itemScope(
                wrappedVaultKeyInformation = wrappedVaultKeyInformation(vaultId),
                wrappedItemKeyInformation = wrappedItemKeyInformation(itemId, vaultId),
            ) { }

            assertTrue(result.isFailure())
            val failure = assertIs<Result.Failure<*, *>>(result)
            assertIs<CryptoScopeError.NoActiveSession>(failure.error)
        }

    @Test
    fun `itemScope keeps the key-wrap cause the session reported`() = runTest {
        val vaultId = newVaultId()
        val itemId = newItemId()
        val cause = KeyWrapException.InvalidKeyLength(expected = 32uL, got = 7uL)

        session.unlockWithArk(ByteArray(32) { it.toByte() })
        session.unwrapVaultKeyFailure = SessionError.KeyWrap(cause)

        val result = provider.itemScope(
            wrappedVaultKeyInformation = wrappedVaultKeyInformation(vaultId),
            wrappedItemKeyInformation = wrappedItemKeyInformation(itemId, vaultId),
        ) { }

        val failure = assertIs<Result.Failure<*, *>>(result)
        val error = assertIs<CryptoScopeError.KeyWrapError>(failure.error)
        assertSame(cause, error.exception)
    }
}
