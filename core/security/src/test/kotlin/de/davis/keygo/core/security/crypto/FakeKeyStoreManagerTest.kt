package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FakeKeyStoreManagerTest {

    private val keyId = KeyId.BackupArkKey

    @Test
    fun `encrypt then decrypt round-trips under the same key id`() {
        val ks = FakeKeyStoreManager()
        val plaintext = ByteArray(32) { it.toByte() }

        val enc = ks.getOrCreateCipherFor(keyId, CryptographicMode.Encrypt)
        val ct = enc.doFinal(plaintext)
        val iv = enc.iv

        val dec = ks.getOrCreateCipherFor(keyId, CryptographicMode.Decrypt, iv)
        assertContentEquals(plaintext, dec.doFinal(ct))
    }

    @Test
    fun `nonce is randomized per encryption`() {
        val ks = FakeKeyStoreManager()
        val a = ks.getOrCreateCipherFor(keyId, CryptographicMode.Encrypt).iv
        val b = ks.getOrCreateCipherFor(keyId, CryptographicMode.Encrypt).iv
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `device locked makes cipher access throw`() {
        val ks = FakeKeyStoreManager(deviceLocked = true)
        assertFailsWith<IllegalStateException> {
            ks.getOrCreateCipherFor(keyId, CryptographicMode.Encrypt)
        }
    }

    @Test
    fun `deleting a key drops the alias and records it`() {
        val keyStoreManager = FakeKeyStoreManager()
        val cipher = keyStoreManager.getOrCreateCipherFor(
            keyId = KeyId.BackupArkKey,
            cryptographicMode = CryptographicMode.Encrypt,
        )
        val ciphertext = cipher.doFinal("ark".encodeToByteArray())

        keyStoreManager.deleteKey(KeyId.BackupArkKey)

        assertFalse(keyStoreManager.keys.keys.contains(KeyId.BackupArkKey))
        // The alias is gone: the next cipher is backed by a brand-new key, so the old
        // ciphertext no longer opens.
        val fresh = keyStoreManager.getOrCreateCipherFor(
            keyId = KeyId.BackupArkKey,
            cryptographicMode = CryptographicMode.Decrypt,
            iv = cipher.iv,
        )
        assertFailsWith<AEADBadTagException> { fresh.doFinal(ciphertext) }
    }
}
