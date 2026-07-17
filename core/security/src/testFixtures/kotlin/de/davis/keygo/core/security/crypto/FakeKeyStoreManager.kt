package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Software AES-256/GCM stand-in for AndroidKeyStore. Keys are generated per alias and kept
 * in-memory, so wrap/unwrap round-trips deterministically in JVM unit tests. Set [deviceLocked]
 * to simulate a key gated by setUnlockedDeviceRequired(true) being used while the device is locked.
 */
class FakeKeyStoreManager(
    var deviceLocked: Boolean = false,
) : KeyStoreManager {

    val keys = mutableMapOf<KeyId, SecretKey>()

    override fun getOrCreateCipherFor(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Cipher {
        if (deviceLocked)
            throw IllegalStateException("device locked")

        val key = keys.getOrPut(keyId) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val mode = when (cryptographicMode) {
            CryptographicMode.Encrypt, CryptographicMode.Wrap -> Cipher.ENCRYPT_MODE
            CryptographicMode.Decrypt, CryptographicMode.Unwrap -> Cipher.DECRYPT_MODE
        }
        if (iv != null) cipher.init(mode, key, GCMParameterSpec(128, iv))
        else cipher.init(mode, key)
        return cipher
    }

    override fun deleteKey(keyId: KeyId) {
        keys.remove(keyId)
    }
}
