package de.davis.keygo.migration.legacy_data.data

import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyRepository
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A plain JCE key stands in for the Keystore one. Nothing here decrypts anything, so the bytes are
 * irrelevant; what matters is whether the alias resolves and how often it is asked.
 */
internal class FakeLegacyKeyRepository(
    private val key: SecretKey? = SecretKeySpec(ByteArray(32), "AES"),
) : LegacyKeyRepository {

    var probes: Int = 0
        private set

    var deleted: Boolean = false
        private set

    override fun secretKey(): SecretKey? {
        probes++
        return key
    }

    override fun deleteLegacyKey() {
        deleted = true
    }
}
