package de.davis.keygo.migration.legacy_data.data.crypto

import org.koin.core.annotation.Single
import java.security.KeyStore
import javax.crypto.SecretKey

internal const val LEGACY_KEY_ALIAS = "password_manager_skey"

/**
 * Supplies the AES key v1 used to encrypt every `SecureElement.data` blob.
 *
 * Returns null when the alias is gone. It must never create a key: v1's own `KeyUtil.getSecretKey`
 * generated one on a miss, and doing the same here would silently turn "this row cannot be read"
 * into "this row decrypts to garbage". A null means the legacy data is unrecoverable, which the
 * caller reports rather than papers over.
 */
internal interface LegacyKeyProvider {

    fun secretKey(): SecretKey?
}

@Single
internal class AndroidLegacyKeyProvider : LegacyKeyProvider {

    override fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(LEGACY_KEY_ALIAS)) return null
        keyStore.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey
    }.getOrNull()
}
