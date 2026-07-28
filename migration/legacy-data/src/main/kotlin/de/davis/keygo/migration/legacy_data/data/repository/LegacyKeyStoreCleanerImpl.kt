package de.davis.keygo.migration.legacy_data.data.repository

import de.davis.keygo.migration.legacy_data.data.crypto.LEGACY_KEY_ALIAS
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyStoreCleaner
import org.koin.core.annotation.Single
import java.security.KeyStore

@Single
internal class LegacyKeyStoreCleanerImpl : LegacyKeyStoreCleaner {

    /**
     * Deletes the alias if it is there, and stays quiet if anything goes wrong.
     *
     * A Keystore that will not open, or an alias that will not delete, leaves a key behind that
     * nothing reads any more. That is untidy rather than harmful, and it runs after the user's data
     * is already in v2, so it has no failure worth propagating into the unlock flow.
     *
     * [containsAlias] guards the delete rather than the delete being attempted blind, so the same
     * call is safe on a v2 install that never had the alias and on a retry that already removed it.
     */
    override fun deleteLegacyKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) keyStore.deleteEntry(LEGACY_KEY_ALIAS)
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
