package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyItemRepository
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyStoreCleaner
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyReadResult

/**
 * In-memory [LegacyItemRepository] that keeps the real contract: every read, prune and count can
 * fail, and a failure is a [Result] rather than a throw.
 *
 * The row count is derived from [readResult] and the ids pruned so far, which is what the real
 * repository reports: the rows still in the file. Tests that need the count to disagree with that
 * (a prune that silently did nothing, a count query that fell over) set [countResult] instead.
 */
internal class FakeLegacyItemRepository : LegacyItemRepository {

    var state: LegacyDatabaseState = LegacyDatabaseState.Present

    var readResult: Result<LegacyReadResult, LegacyReadFailure> =
        Result.Success(LegacyReadResult(emptyList(), emptyList()))

    var pruneResult: Result<Unit, LegacyReadFailure> = Result.Success(Unit)

    /** Overrides the derived row count when non-null. */
    var countResult: Result<Int, LegacyReadFailure>? = null

    /** What [deleteDatabase] answers, so a file that refuses to go away can be modelled. */
    var deleteSucceeds: Boolean = true

    override var repairedRows: Int = 0

    val prunedIds = mutableListOf<Long>()

    var databaseDeleted: Boolean = false
        private set

    var readCalls: Int = 0
        private set

    override suspend fun state(): LegacyDatabaseState = state

    override suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure> {
        readCalls++
        return readResult
    }

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
        is Result.Success -> result.success.items.size + result.success.failures.size -
            prunedIds.size

        is Result.Failure -> 0
    }
}

internal class FakeLegacyKeyStoreCleaner : LegacyKeyStoreCleaner {

    var deleted: Boolean = false
        private set

    override fun deleteLegacyKey() {
        deleted = true
    }
}
