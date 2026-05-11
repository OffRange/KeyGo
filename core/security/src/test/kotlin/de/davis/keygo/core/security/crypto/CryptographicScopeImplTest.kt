package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
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

    private val session: Session = FakeSession(startOnConstruct = true)
    private val itemManager = FakeItemManager()
    private val keyWrapper = FakeKeyWrapper()

    private val provider = CryptographicScopeProviderImpl(session, itemManager, keyWrapper)

    private val label = "password"

    private fun wrappedVaultKeyInformation(
        vaultId: UUID = UUID.randomUUID(),
    ): WrappedVaultKeyInformation {
        val blob = keyWrapper.wrapVaultKey(
            ark = session.ark,
            vaultKey = ByteArray(32) { random.nextBytes(1)[0] },
            vaultId = vaultId,
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
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(label) }

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
            val a = first.encrypt(label)
            val b = second.encrypt(label)
            (a to b) to wrapCurrentItemKey()
        }

        provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) {
            assertContentEquals(first, encrypted.first.decrypt(label))
            assertContentEquals(second, encrypted.second.decrypt(label))
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
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

        val otherVault = wrappedVaultKeyInformation()
        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = otherVault,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(label) }
        }
    }

    @Test
    fun `decrypting with a different label fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(label = "password") to wrapCurrentItemKey()
        }

        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(label = "totp_secret") }
        }
    }

    @Test
    fun `decrypting with a different itemAad under same label fails`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "secret".toByteArray()

        val (cipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = itemAad(vaultInfo.vaultId),
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(label) }
        }
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
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

        assertFailsWith<Exception> {
            provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = itemAad(vaultInfo.vaultId),
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(label) }
        }
    }

    @Test
    fun `create flow exposes a non-empty wrapped item key`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()

        val wrapped = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = itemAad(vaultInfo.vaultId)),
        ) { wrapCurrentItemKey() }

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
            ByteArray(0).encrypt(label) to wrapCurrentItemKey()
        }

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(label) }

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
            List(attempts) { plaintext.encrypt(label) }
        }

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
    fun `different labels yield different ciphertext for same plaintext`() = runTest {
        val vaultInfo = wrappedVaultKeyInformation()
        val aad = itemAad(vaultInfo.vaultId)
        val plaintext = "Same data, different field".toByteArray()

        val (passwordCipher, totpCipher, wrapped) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            Triple(
                plaintext.encrypt(label = "password"),
                plaintext.encrypt(label = "totp_secret"),
                wrapCurrentItemKey(),
            )
        }

        assertTrue(!passwordCipher.data.contentEquals(totpCipher.data))

        val (recoveredPassword, recoveredTotp) = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) {
            passwordCipher.decrypt(label = "password") to totpCipher.decrypt(label = "totp_secret")
        }

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
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

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
            ) { tampered.decrypt(label) }
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
                    val encrypted = plaintext.encrypt(label)
                    val decrypted = encrypted.decrypt(label)
                    plaintext.contentEquals(decrypted)
                }
            }
            jobs.awaitAll()
        }

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
            plaintext.encrypt(label) to wrapCurrentItemKey()
        }

        val recovered = provider.itemScope(
            wrappedVaultKeyInformation = vaultInfo,
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = aad,
                wrappedItemKey = wrapped,
            ),
        ) { cipher.decrypt(label) }

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
                plaintext.encrypt(label) to wrapCurrentItemKey()
            }

            val recovered = provider.itemScope(
                wrappedVaultKeyInformation = vaultInfo,
                wrappedItemKeyInformation = WrappedItemKeyInformation(
                    itemAad = aad,
                    wrappedItemKey = wrapped,
                ),
            ) { cipher.decrypt(label) }

            assertContentEquals(plaintext, recovered, "size=$size should round-trip")
        }
    }
}
