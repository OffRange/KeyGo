package de.davis.keygo.core.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import de.davis.keygo.core.domain.crypto.CryptographicConstants
import de.davis.keygo.core.domain.crypto.EncryptionKeyProvider
import de.davis.keygo.core.domain.model.crypto.AesKey
import de.davis.keygo.core.domain.model.crypto.asAesKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class EncryptionKeyProviderImpl : EncryptionKeyProvider {

    override fun provide(): AesKey = KeyStore.getInstance(ANDROID_KEY_STORE_NAME).apply {
        load(null)
    }.run {
        when {
            containsAlias(KEY_ALIAS) -> getKey(KEY_ALIAS, null) as SecretKey

            else -> aesKeyGenerator {
                setKeySize(CryptographicConstants.KEY_LENGTH)
                setBlockModes(CryptographicConstants.BLOCK_MODE)
                setEncryptionPaddings(CryptographicConstants.PADDING_MODE)
            }.generateKey()
        }
    }.asAesKey()

    private fun aesKeyGenerator(
        paramBuilder: KeyGenParameterSpec.Builder.() -> Unit
    ) = KeyGenerator.getInstance(CryptographicConstants.ALGORITHM, ANDROID_KEY_STORE_NAME).apply {
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply(paramBuilder)
            .build()
            .also(this::init)
    }

    companion object {
        private const val ANDROID_KEY_STORE_NAME = "AndroidKeyStore"

        private const val KEY_ALIAS: String = "password_manager_skey"
    }
}