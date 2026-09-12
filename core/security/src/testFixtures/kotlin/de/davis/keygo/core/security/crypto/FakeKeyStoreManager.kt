package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.Result
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Software AES-256/GCM stand-in for AndroidKeyStore. Keys are generated per alias and kept
 * in-memory, so wrap/unwrap round-trips deterministically in JVM unit tests.
 *
 * Two ways to make a cipher request fail. [deviceLocked] models a key gated by
 * setUnlockedDeviceRequired(true) being used while the device is locked, which is what the real
 * keystore answers with [KeyStoreManagerError.AuthenticationRequired]. [failure] is the general
 * form: it hands back whatever the caller wants to be told, so the paths that only a permanently
 * invalidated key reaches can be driven from a test at all.
 */
class FakeKeyStoreManager(
    var deviceLocked: Boolean = false,
    var failure: KeyStoreManagerError? = null,
) : KeyStoreManager {

    val keys = mutableMapOf<KeyId, SecretKey>()

    override fun getOrCreateCipherFor(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Result<Cipher, KeyStoreManagerError> {
        failure?.let { return Result.Failure(it) }
        if (deviceLocked) return Result.Failure(KeyStoreManagerError.AuthenticationRequired)

        return createCipher(keyId, cryptographicMode, iv)
    }

    private fun createCipher(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Result<Cipher, KeyStoreManagerError> = runCatching {
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
        cipher
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(KeyStoreManagerError.Unknown) },
    )

    override fun deleteKey(keyId: KeyId) {
        keys.remove(keyId)
    }
}
