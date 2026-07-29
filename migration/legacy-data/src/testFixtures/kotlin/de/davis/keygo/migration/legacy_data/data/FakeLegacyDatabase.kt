package de.davis.keygo.migration.legacy_data.data

import androidx.room3.InvalidationTracker
import de.davis.keygo.migration.legacy_data.data.local.dao.LegacyElementDao
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase

/**
 * A plain subclass rather than Room's builder, for tests that only ever reach [legacyElementDao].
 *
 * That is what lets a fake DAO be put underneath a real repository without a database file or a
 * mock of the Room-generated container. Everything Room itself would use fails loudly, because this
 * stands in for the container alone and not for a database.
 */
internal class FakeLegacyDatabase(private val dao: LegacyElementDao) : LegacyDatabase() {

    override fun legacyElementDao(): LegacyElementDao = dao

    override fun createInvalidationTracker(): InvalidationTracker = unmodelled()

    override suspend fun clearAllTables(): Unit = unmodelled()

    private fun unmodelled(): Nothing =
        error("FakeLegacyDatabase is only ever read and pruned through")
}
