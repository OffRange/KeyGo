package de.davis.keygo.core.security.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.annotation.RequiresApi
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.CipherKeyId
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.security.domain.model.SigningKeyId
import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Single
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Single
internal class KeyStoreManagerImpl : KeyStoreManager {

    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    private val signingKeyLock = Any()

    override fun getOrCreateCipherFor(
        keyId: CipherKeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Result<Cipher, KeyStoreManagerError> = runCatching {
        val alias = keyId.id
        val key = when (keyStore.containsAlias(alias)) {
            true -> keyStore.getKey(alias, null)
            else -> createKeyFor(keyId)
        }

        val cipher = Cipher.getInstance("$ALGORITHM/$BLOCK_MODE/$PADDING_MODE")
        val cipherMode = when (cryptographicMode) {
            CryptographicMode.Wrap -> Cipher.WRAP_MODE
            CryptographicMode.Unwrap -> Cipher.UNWRAP_MODE

            CryptographicMode.Encrypt -> Cipher.ENCRYPT_MODE
            CryptographicMode.Decrypt -> Cipher.DECRYPT_MODE
        }

        val params = iv?.let {
            GCMParameterSpec(T_LEN, iv)
        }

        cipher.init(cipherMode, key, params)
        cipher
    }.toKeyStoreResult { "Could not initialise a cipher for $keyId" }

    override fun getOrCreatePublicKey(
        keyId: SigningKeyId,
    ): Result<PublicKey, KeyStoreManagerError> = runCatching {
        getOrCreateKeyPair(keyId).public
    }.toKeyStoreResult { "Could not read the public key of $keyId" }

    override fun sign(
        keyId: SigningKeyId,
        message: ByteArray,
    ): Result<ByteArray, KeyStoreManagerError> = runCatching {
        // A fresh Signature per call: the instance is stateful and not thread-safe.
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(getOrCreateKeyPair(keyId).private)
            update(message)
            sign()
        }
    }.toKeyStoreResult { "Could not sign with $keyId" }

    override fun deleteKey(keyId: KeyId) {
        if (keyStore.containsAlias(keyId.id)) keyStore.deleteEntry(keyId.id)
    }

    // ------------------ CipherKey ------------------
    private fun createKeyFor(keyId: CipherKeyId): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            keyId.id,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(BLOCK_MODE)
            setEncryptionPaddings(PADDING_MODE)

            applyAccessPolicy(keyId)

            setRandomizedEncryptionRequired(true)

            // No setIsStrongBoxBacked here: some StrongBox implementations (reported on a
            // Redmi Note 14 Pro+ 5G running Android 16) return AES-GCM ciphertext under an
            // auth-bound key that the same StrongBox then refuses to verify, failing every
            // unwrap with VERIFICATION_FAILED even in the process that produced it.
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            //     setIsStrongBoxBacked(
            //         applicationContext.packageManager.hasSystemFeature(
            //             PackageManager.FEATURE_STRONGBOX_KEYSTORE
            //         )
            //     )
        }.build()

        val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
        generator.init(spec)

        return generator.generateKey()
    }


    // ------------------ SigningKey ------------------
    private fun getOrCreateKeyPair(keyId: SigningKeyId): KeyPair = synchronized(signingKeyLock) {
        when (val entry = keyStore.getEntry(keyId.id, null)) {
            is KeyStore.PrivateKeyEntry -> KeyPair(entry.certificate.publicKey, entry.privateKey)
            null -> createKeyPairFor(keyId)
            else -> error("$keyId is not a key pair")
        }
    }

    private fun createKeyPairFor(keyId: SigningKeyId): KeyPair {
        val spec = KeyGenParameterSpec.Builder(
            keyId.id,
            KeyProperties.PURPOSE_SIGN
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            setDigests(KeyProperties.DIGEST_SHA256)

            applyAccessPolicy(keyId)
        }.build()

        val generator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(spec)

        return generator.generateKeyPair()
    }

    private fun KeyGenParameterSpec.Builder.applyAccessPolicy(keyId: KeyId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUnlockedDeviceRequired(true)

        setUserAuthenticationRequired(keyId.needsAuthentication)
        setInvalidatedByBiometricEnrollment(keyId.needsAuthentication)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            setUserAuthenticationParameters(
                INVALIDATE_IMMEDIATE_R,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        else
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(INVALIDATE_IMMEDIATE)
    }

    private inline fun <T> kotlin.Result<T>.toKeyStoreResult(
        message: () -> String,
    ): Result<T, KeyStoreManagerError> = fold(
        onSuccess = { Result.Success(it) },
        onFailure = {
            Log.e(TAG, message(), it)
            Result.Failure(keyStoreManagerErrorFrom(it))
        },
    )

    companion object {
        private const val TAG = "KeyStoreManager"

        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING_MODE = KeyProperties.ENCRYPTION_PADDING_NONE

        private const val T_LEN = 128

        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"


        private const val INVALIDATE_IMMEDIATE = -1

        @RequiresApi(Build.VERSION_CODES.R)
        private const val INVALIDATE_IMMEDIATE_R = 0
    }
}

fun keyStoreManagerErrorFrom(throwable: Throwable): KeyStoreManagerError {
    val causes = generateSequence(throwable) { current -> current.cause?.takeIf { it !== current } }
        .take(MAX_CAUSE_DEPTH)
        .toList()

    return when {
        causes.any { it is KeyPermanentlyInvalidatedException || it is AEADBadTagException }
            -> KeyStoreManagerError.KeyInvalidated

        causes.any { it is UserNotAuthenticatedException } -> KeyStoreManagerError.AuthenticationRequired

        else -> KeyStoreManagerError.Unknown
    }
}

private const val MAX_CAUSE_DEPTH = 8