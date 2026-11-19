package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.CipherKeyId
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.model.SigningKeyId
import de.davis.keygo.core.util.Result
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Software stand-in for AndroidKeyStore: AES-256/GCM for [CipherKeyId]s, P-256 ECDSA for
 * [SigningKeyId]s. Keys are generated per alias and kept in-memory, so wrap/unwrap and sign/verify
 * round-trip deterministically in JVM unit tests.
 *
 * Two ways to make a request fail. [deviceLocked] models a key gated by
 * setUnlockedDeviceRequired(true) being used while the device is locked, which is what the real
 * keystore answers with [KeyStoreManagerError.AuthenticationRequired]. [failure] is the general
 * form: it hands back whatever the caller wants to be told, so the paths that only a permanently
 * invalidated key reaches can be driven from a test at all.
 */
class FakeKeyStoreManager(
    var deviceLocked: Boolean = false,
    var failure: KeyStoreManagerError? = null,
) : KeyStoreManager {

    val keys = mutableMapOf<CipherKeyId, SecretKey>()

    val keyPairs = mutableMapOf<SigningKeyId, KeyPair>()

    override fun getOrCreateCipherFor(
        keyId: CipherKeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Result<Cipher, KeyStoreManagerError> {
        failure?.let { return Result.Failure(it) }
        if (deviceLocked) return Result.Failure(KeyStoreManagerError.AuthenticationRequired)

        return createCipher(keyId, cryptographicMode, iv)
    }

    override fun getOrCreatePublicKey(
        keyId: SigningKeyId,
    ): Result<PublicKey, KeyStoreManagerError> = guarded { keyPairFor(keyId).public }

    override fun sign(
        keyId: SigningKeyId,
        message: ByteArray,
    ): Result<ByteArray, KeyStoreManagerError> = guarded {
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPairFor(keyId).private)
            update(message)
            sign()
        }
    }

    private fun keyPairFor(keyId: SigningKeyId): KeyPair = synchronized(keyPairs) {
        keyPairs.getOrPut(keyId) {
            KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        }
    }

    private inline fun <T> guarded(block: () -> T): Result<T, KeyStoreManagerError> {
        failure?.let { return Result.Failure(it) }
        if (deviceLocked) return Result.Failure(KeyStoreManagerError.AuthenticationRequired)

        return runCatching(block).fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(KeyStoreManagerError.Unknown) },
        )
    }

    private fun createCipher(
        keyId: CipherKeyId,
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
        when (keyId) {
            is CipherKeyId -> keys.remove(keyId)
            is SigningKeyId -> synchronized(keyPairs) { keyPairs.remove(keyId) }
        }
    }
}
