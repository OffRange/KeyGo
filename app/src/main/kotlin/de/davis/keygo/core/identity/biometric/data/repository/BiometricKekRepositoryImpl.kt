package de.davis.keygo.core.identity.biometric.data.repository

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import de.davis.keygo.core.identity.biometric.domain.model.KeyStoreError
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricKekRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicConstants
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import de.davis.keygo.core.security.domain.crypto.model.asAesKey
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import org.koin.core.annotation.Single
import java.security.KeyStore
import javax.crypto.KeyGenerator

@Single
@Deprecated("Migrate to :core:security")
class BiometricKekRepositoryImpl(
    private val keyStore: KeyStore,
) : BiometricKekRepository {

    override fun hasKek(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    override fun getKek(): Result<AesKey, KeyStoreError> = keyStore.getKey(KEY_ALIAS, null)
        ?.asAesKey()
        .asResult(onNullError = KeyStoreError.KeyNotFound)


    @Suppress("DEPRECATION")
    override fun createKek(): AesKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(CryptographicConstants.BLOCK_MODE)
            setEncryptionPaddings(CryptographicConstants.PADDING_MODE)

            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    INVALIDATE_IMMEDIATE_R,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                setUserAuthenticationValidityDurationSeconds(INVALIDATE_IMMEDIATE)
            }
        }.build()

        val generator =
            KeyGenerator.getInstance(CryptographicConstants.ALGORITHM, "AndroidKeyStore")
        generator.init(spec)

        return generator.generateKey().asAesKey()
    }

    companion object {
        @VisibleForTesting
        internal const val KEY_ALIAS = "biometric_kek"
        const val INVALIDATE_IMMEDIATE = -1

        @RequiresApi(Build.VERSION_CODES.R)
        const val INVALIDATE_IMMEDIATE_R = 0
    }
}