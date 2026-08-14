package de.davis.keygo.legacy_migration.data

import de.davis.keygo.core.util.Result
import de.davis.keygo.legacy_migration.domain.model.LegacyReadFailure
import de.davis.keygo.legacy_migration.domain.repository.LegacyItemRepository
import de.davis.keygo.legacy_migration.domain.repository.LegacyReadResult

/**
 * In-memory [LegacyItemRepository] that keeps the real contract: every read, prune and count can
 * fail, and a failure is a [Result] rather than a throw.
 *
 * The row count is derived from [readResult] and the ids pruned so far, which is what the real
 * repository reports: the rows still in the file. Tests that need the count to disagree with that
 * (a prune that silently did nothing, a count query that fell over) set [countResult] instead.
 */
internal class FakeLegacyItemRepository : LegacyItemRepository {

    var readResult: Result<LegacyReadResult, LegacyReadFailure> =
        Result.Success(LegacyReadResult(emptyList(), emptyList(), FAKE_LEGACY_KEY))

    var pruneResult: Result<Unit, LegacyReadFailure> = Result.Success(Unit)

    /** Overrides the derived row count when non-null. */
    var countResult: Result<Int, LegacyReadFailure>? = null

    /** What [deleteDatabase] answers, so a file that refuses to go away can be modelled. */
    var deleteSucceeds: Boolean = true

    val prunedIds = mutableListOf<Long>()

    var databaseDeleted: Boolean = false
        private set

    override suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure> = readResult

    override suspend fun prune(legacyIds: List<Long>): Result<Unit, LegacyReadFailure> {
        if (pruneResult is Result.Success) prunedIds += legacyIds
        return pruneResult
    }

    override suspend fun remainingCount(): Result<Int, LegacyReadFailure> =
        countResult ?: Result.Success(rowsInFile())

    override fun deleteDatabase(): Boolean {
        databaseDeleted = deleteSucceeds
        return deleteSucceeds
    }

    private fun rowsInFile(): Int = when (val result = readResult) {
        is Result.Success -> (result.success.items.size + result.success.failures.size -
                prunedIds.size).coerceAtLeast(0)

        is Result.Failure -> 0
    }
}
