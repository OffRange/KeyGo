package de.davis.keygo.migration.legacy_data.domain.repository

import javax.crypto.SecretKey

/** Reaches v1's Keystore alias, the key every blob in the inherited database was written under. */
internal interface LegacyKeyRepository {

    /** Returns null when the alias is gone, which no blob in the file can survive. */
    fun secretKey(): SecretKey?

    /**
     * Removes v1's alias. Only ever called once the file it protected has provably gone, because a
     * key removed while encrypted rows are still on disk makes them unreadable for good.
     */
    fun deleteLegacyKey()
}
