package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.FakeItemManager
import de.davis.keygo.rust.FakeKeyWrapper
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptographicScopeImplTest {

    private val random = Random(42)

    private val session = FakeSession(startUnlocked = true)
    private val itemRepository = FakeItemRepository()
    private val itemManager = FakeItemManager()
    private val keyWrapper = FakeKeyWrapper()

    private val provider = CryptographicScopeProviderImpl(
        session,
        itemRepository,
        itemManager,
        keyWrapper,
    )

    private val kind = SecretKind.Password

    private suspend fun wrappedVaultKeyInformation(
        vaultId: UUID = UUID.randomUUID(),
    ): WrappedVaultKeyInformation {
        val blob = checkNotNull(
            session.wrapVaultKey(
                vaultKey = ByteArray(32) { random.nextBytes(1)[0] },
                vaultId = vaultId,
            ).getOrNull(),
        )
        return WrappedVaultKeyInformation(
            wrappedVaultKey = KeyInformation(
                wrappedKey = blob.ciphertext,
                keyNonce = blob.nonce,
            ),
            vaultId = vaultId,
        )
    }

    private fun itemAad(vaultId: UUID = UUID.randomUUID()) =
        ItemAad(itemId = UUID.randomUUID(), vaultId = vaultId)

    @Test
    fun `round-trips a payload using the same generated item key`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "item secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertSuccess()

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `multiple operations within one scope share the same item key`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val first = "first".toByteArray()
        val second = "second".toByteArray()

        val (encrypted, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            val a = first.encrypt(kind)
            val b = second.encrypt(kind)
            (a to b) to wrapCurrentItemKey()
        }.assertSuccess()

        provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) {
            assertContentEquals(first, encrypted.first.decrypt(kind))
            assertContentEquals(second, encrypted.second.decrypt(kind))
        }
    }

    @Test
    fun `decrypting with the wrong vault key fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        val otherVault = wrappedVaultKeyInformation()
        provider.itemScope(
            wrappedVaultKeyInformation = otherVault,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertFailure()
    }

    @Test
    fun `decrypting with a different kind fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(SecretKind.Password) to wrapCurrentItemKey()
        }.assertSuccess()

        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(SecretKind.TotpSecret) }
        }
    }

    @Test
    fun `decrypting with a different itemAad under same kind fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = itemAad(vaultInfo.vaultId),
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertFailure()
    }

    @Test
    fun `unwrapping the item key with the wrong itemAad fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = itemAad(vaultInfo.vaultId),
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertFailure()
    }

    @Test
    fun `create flow exposes a non-empty wrapped item key`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()

        val wrapped = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = itemAad(vaultInfo.vaultId)),
        ) { wrapCurrentItemKey() }.assertSuccess()

        assertNotNull(wrapped)
        assertTrue(wrapped.wrappedKey.isNotEmpty())
        assertTrue(wrapped.keyNonce.isNotEmpty())
    }

    @Test
    fun `empty data round-trips`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            ByteArray(0).encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertSuccess()

        assertContentEquals(ByteArray(0), recovered)
    }

    @Test
    fun `repeated encryption yields unique ciphertexts and IVs`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "Sensitive secret data".toByteArray()
        val attempts = 5

        val ciphertexts = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            List(attempts) { plaintext.encrypt(kind) }
        }.assertSuccess()

        val uniqueCiphertexts = ciphertexts.map { it.data.contentToString() }.toSet()
        val uniqueIvs = ciphertexts.map { it.iv.contentToString() }.toSet()

        assertEquals(
            attempts,
            uniqueIvs.size,
            "Expected $attempts unique IVs. The IV generation might be deterministic or repeating."
        )

        assertEquals(
            attempts,
            uniqueCiphertexts.size,
            "Expected $attempts unique ciphertexts. Encryption is not properly randomized."
        )
    }

    @Test
    fun `different kinds yield different ciphertext for same plaintext`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "Same data, different field".toByteArray()

        val (passwordCipher, totpCipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            Triple(
                plaintext.encrypt(SecretKind.Password),
                plaintext.encrypt(SecretKind.TotpSecret),
                wrapCurrentItemKey(),
            )
        }.assertSuccess()

        assertTrue(!passwordCipher.data.contentEquals(totpCipher.data))

        val (recoveredPassword, recoveredTotp) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) {
            passwordCipher.decrypt(SecretKind.Password) to totpCipher.decrypt(SecretKind.TotpSecret)
        }.assertSuccess()

        assertContentEquals(plaintext, recoveredPassword)
        assertContentEquals(plaintext, recoveredTotp)
    }

    @Test
    fun `tampered ciphertext fails to decrypt`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "This data will be corrupted!".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        val tampered = CryptographicData(
            data = cipher.data.clone().apply {
                val middle = size / 2
                this[middle] = (this[middle].toInt() xor 0xFF).toByte()
            },
            iv = cipher.iv,
        )

        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { tampered.decrypt(kind) }
        }
    }

    @Test
    fun `concurrent encrypt and decrypt succeeds`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "Concurrent encryption test".toByteArray()

        val results = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            val jobs: List<Deferred<Boolean>> = List(10) {
                async {
                    val encrypted = plaintext.encrypt(kind)
                    val decrypted = encrypted.decrypt(kind)
                    plaintext.contentEquals(decrypted)
                }
            }
            jobs.awaitAll()
        }.assertSuccess()

        assertTrue(
            results.all { it },
            "All concurrent encrypt/decrypt operations should return original data",
        )
    }

    @Test
    fun `large data round-trips`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = ByteArray(1_000_000) { random.nextBytes(1)[0] }

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(kind) to wrapCurrentItemKey()
        }.assertSuccess()

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(kind) }.assertSuccess()

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun `random sized payloads round-trip`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()

        repeat(10) {
            val aad = itemAad(vaultInfo.vaultId)
            val size = random.nextInt(1, 65_536)
            val plaintext = ByteArray(size) { random.nextBytes(1)[0] }

            val (cipher, wrapped) = provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
            ) {
                plaintext.encrypt(kind) to wrapCurrentItemKey()
            }.assertSuccess()

            val recovered = provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(kind) }.assertSuccess()

            assertContentEquals(plaintext, recovered, "size=$size should round-trip")
        }
    }
}
