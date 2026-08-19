package de.davis.keygo.legacy_migration.data

import de.davis.keygo.legacy_migration.data.local.dao.LegacyElementDao
import de.davis.keygo.legacy_migration.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.legacy_migration.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.legacy_migration.data.local.entity.LegacyTagEntity
import de.davis.keygo.legacy_migration.data.local.pojo.LegacyElementWithTags

/**
 * In-memory [LegacyElementDao] over a set of row ids, for the tests that are about counting and
 * pruning rather than about what is in a row.
 *
 * Deletes really remove, so [remainingIds] and [count] answer what a real file would after a prune,
 * and [deleteCalls] records how the prune was split up. Anything this fake does not model fails
 * loudly rather than answering with a default, because a test that quietly reaches an unmodelled
 * call is a test that proves nothing.
 */
internal class FakeLegacyElementDao(ids: Set<Long> = emptySet()) : LegacyElementDao {

    private val ids = ids.toMutableSet()

    /** Thrown by [count], so a read can fail the way a cancelled unlock scope does. */
    var countFailure: Throwable? = null

    /** The ids each [deleteByIds] call carried, in order. */
    val deleteCalls = mutableListOf<List<Long>>()

    val remainingIds: Set<Long> get() = ids

    override suspend fun count(): Int {
        countFailure?.let { throw it }
        return ids.size
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        deleteCalls += ids
        this.ids -= ids.toSet()
    }

    override suspend fun getAllWithTags(): List<LegacyElementWithTags> = unmodelled()

    override suspend fun insertElement(element: LegacySecureElementEntity): Long = unmodelled()

    override suspend fun insertTag(tag: LegacyTagEntity): Long = unmodelled()

    override suspend fun insertCrossRef(crossRef: LegacySecureElementTagCrossRef): Unit =
        unmodelled()

    private fun unmodelled(): Nothing = error("FakeLegacyElementDao does not model this call")
}
