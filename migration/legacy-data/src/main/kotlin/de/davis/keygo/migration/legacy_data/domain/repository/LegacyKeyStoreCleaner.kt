package de.davis.keygo.migration.legacy_data.domain.repository

/**
 * Removes v1's AES alias from the Android Keystore.
 *
 * The alias is what makes every blob in the inherited file readable, so removing it is as final as
 * deleting the file itself. It may only run once the file is provably gone: while any encrypted row
 * is still on disk, the key is the only thing that could ever open it again.
 *
 * It must never create the alias. v1's own `KeyUtil.getSecretKey` generated one on a miss, and a
 * generated key here would turn "this data is unreadable" into "this data decrypts to garbage".
 */
internal interface LegacyKeyStoreCleaner {

    fun deleteLegacyKey()
}
