package de.davis.keygo.core.security.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import de.davis.keygo.core.security.domain.KeyStoreManager
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import org.koin.core.annotation.Single
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Single
internal class KeyStoreManagerImpl(
    private val applicationContext: Context
) : KeyStoreManager {

    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    override fun getOrCreateCipherFor(
        keyId: KeyId,
        cryptographicMode: CryptographicMode,
        iv: ByteArray?,
    ): Cipher {
        val alias = keyId.id

        val key = when (keyStore.containsAlias(alias)) {
            true -> keyStore.getKey(alias, null)
            else -> createKeyFor(alias)
        }

        val cipher = Cipher.getInstance("$ALGORITHM/$BLOCK_MODE/$PADDING_MODE")
        val cipherMode = when (cryptographicMode) {
            CryptographicMode.Wrap -> Cipher.WRAP_MODE
            is CryptographicMode.Unwrap -> Cipher.UNWRAP_MODE
        }

        val params = iv?.let {
            GCMParameterSpec(T_LEN, iv)
        }

        cipher.init(cipherMode, key, params)
        return cipher
    }

    private fun createKeyFor(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(BLOCK_MODE)
            setEncryptionPaddings(PADDING_MODE)

            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)

            setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                setIsStrongBoxBacked(
                    applicationContext.packageManager.hasSystemFeature(
                        PackageManager.FEATURE_STRONGBOX_KEYSTORE
                    )
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                setUserAuthenticationParameters(
                    INVALIDATE_IMMEDIATE_R,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            else
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(INVALIDATE_IMMEDIATE)

        }.build()

        val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
        generator.init(spec)

        return generator.generateKey()
    }

    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING_MODE = KeyProperties.ENCRYPTION_PADDING_NONE

        private const val T_LEN = 128


        private const val INVALIDATE_IMMEDIATE = -1

        @RequiresApi(Build.VERSION_CODES.R)
        private const val INVALIDATE_IMMEDIATE_R = 0
    }
}