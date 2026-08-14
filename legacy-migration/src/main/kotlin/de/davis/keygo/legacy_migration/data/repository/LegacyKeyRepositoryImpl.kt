package de.davis.keygo.legacy_migration.data.repository

import de.davis.keygo.legacy_migration.domain.repository.LegacyKeyRepository
import org.koin.core.annotation.Single
import java.security.KeyStore
import javax.crypto.SecretKey

/**
 * v1's Keystore alias. The identity of a shipped v1 install's item key. It must never change.
 */
internal const val LEGACY_KEY_ALIAS = "password_manager_skey"

@Single
internal class LegacyKeyRepositoryImpl : LegacyKeyRepository {

    override fun secretKey(): SecretKey? =
        withAlias { getKey(LEGACY_KEY_ALIAS, null) as? SecretKey }

    override fun deleteLegacyKey() {
        withAlias { deleteEntry(LEGACY_KEY_ALIAS) }
    }

    /**
     * Runs [block] against the Keystore only when v1's alias is actually there, and never throws.
     * A Keystore that will not load, or an alias that is already gone, is the same answer to both
     * callers: there is no legacy key.
     */
    private fun <T> withAlias(block: KeyStore.() -> T): T? = runCatching {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) keyStore.block() else null
    }.getOrNull()

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
