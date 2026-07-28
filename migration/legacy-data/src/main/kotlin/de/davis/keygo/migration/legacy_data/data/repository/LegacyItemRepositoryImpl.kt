package de.davis.keygo.migration.legacy_data.data.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyKeyProvider
import de.davis.keygo.migration.legacy_data.data.json.LegacyDetailParser
import de.davis.keygo.migration.legacy_data.data.local.dao.LegacyElementDao
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.pojo.LegacyElementWithTags
import de.davis.keygo.migration.legacy_data.data.mapper.toLegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseFiles
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyItemRepository
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyReadResult
import org.koin.core.annotation.Single

@Single
internal class LegacyItemRepositoryImpl(
    private val databaseProvider: LegacyDatabaseProvider,
    private val keyProvider: LegacyKeyProvider,
    private val cipher: LegacyCipher,
    private val parser: LegacyDetailParser,
    private val databaseFiles: LegacyDatabaseFiles,
) : LegacyItemRepository {

    override val repairedRows: Int get() = databaseProvider.repairedRows

    override suspend fun state(): LegacyDatabaseState {
        // Asked first, and of the file rather than of the provider. The provider also comes back
        // empty for a file that is simply not there, and `Absent` and `Unreadable` lead to opposite
        // places: one is a clean install with nothing to do, the other is a file we must not touch.
        if (!databaseFiles.exists()) return LegacyDatabaseState.Absent
        // Past that check the provider can only come back empty for a file it could not repair, so
        // this is the one place `Unreadable` can be told apart from a file Room does not recognise.
        if (databaseProvider.get() == null) return LegacyDatabaseState.Unreadable

        // Room opens the file on the first query rather than when the object is built, so querying
        // is the probe. A leftover v2 database from before `ItemDatabase` was renamed has no
        // SecureElement table, Room refuses to validate it, and that is what NotLegacy means. The
        // null check above has already taken the unreadable case out of this branch.
        return withDao { it.count() }.fold(
            onSuccess = { LegacyDatabaseState.Present },
            onFailure = { LegacyDatabaseState.NotLegacy },
        )
    }

    override suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure> = resultBinding {
        val rows = withDao { it.getAllWithTags() }.bind()

        // Probed once for the whole run, never once per row. `LegacyCipher.decrypt` folds four
        // different failures into one null, and a gone alias is the only one of them that says
        // nothing in this file is recoverable. Letting it arrive as one Undecryptable per row would
        // tell the user every entry was individually damaged when the entries are intact, and it
        // could not be told apart from a file that really is corrupt end to end.
        keyProvider.secretKey().asResult(LegacyReadFailure.KeyUnavailable).bind()

        val items = mutableListOf<LegacyItem>()
        val failures = mutableListOf<LegacyRowFailure>()

        for (row in rows) {
            val decrypted = cipher.decrypt(row.element.data)
            if (decrypted == null) {
                failures += row.failure(LegacyFailureReason.Undecryptable)
                continue
            }

            val detail = parser.parse(decrypted)
            if (detail == null) {
                failures += row.failure(LegacyFailureReason.Unparseable)
                continue
            }

            items += row.toLegacyItem(detail)
        }

        LegacyReadResult(items = items, failures = failures)
    }

    override suspend fun prune(legacyIds: List<Long>): Result<Unit, LegacyReadFailure> {
        if (legacyIds.isEmpty()) return Result.Success(Unit)
        return withDao { it.deleteByIds(legacyIds) }
    }

    override suspend fun remainingCount(): Result<Int, LegacyReadFailure> = withDao { it.count() }

    override fun deleteDatabase(): Boolean {
        databaseProvider.close()
        return databaseFiles.delete()
    }

    /**
     * Runs [block] against the legacy DAO, turning both ways the file can refuse to be read into
     * the same failure: the provider could not repair it, or Room could not validate it on the
     * first query. Neither leaves anything to import, and neither should surface as a raw throw.
     */
    private suspend fun <T> withDao(
        block: suspend (LegacyElementDao) -> T,
    ): Result<T, LegacyReadFailure> {
        val dao = databaseProvider.get()?.legacyElementDao()
            ?: return Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        return runCatching { block(dao) }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(LegacyReadFailure.DatabaseUnreadable) },
        )
    }

    private fun LegacyElementWithTags.failure(reason: LegacyFailureReason) =
        LegacyRowFailure(legacyId = element.id, title = element.title, reason = reason)
}
