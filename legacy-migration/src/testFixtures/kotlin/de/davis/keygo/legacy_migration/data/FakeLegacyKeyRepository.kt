package de.davis.keygo.legacy_migration.data

import de.davis.keygo.legacy_migration.domain.repository.LegacyKeyRepository
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A plain JCE key stands in for the Keystore one. Nothing here decrypts anything, so the bytes are
 * irrelevant; what matters is whether the alias resolves and how often it is asked.
 */
internal val FAKE_LEGACY_KEY: SecretKey = SecretKeySpec(ByteArray(32), "AES")

internal class FakeLegacyKeyRepository(
    private val key: SecretKey? = FAKE_LEGACY_KEY,
) : LegacyKeyRepository {

    var probes: Int = 0
        private set

    var deleted: Boolean = false
        private set

    override suspend fun secretKey(): SecretKey? {
        probes++
        return key
    }

    override suspend fun deleteLegacyKey() {
        deleted = true
    }
}
