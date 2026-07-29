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
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacySecureElementProbe
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * SQLite's bound-parameter limit was 999 until 3.32.0, and that ceiling is what Android's system
 * SQLite still enforces through API 30. A single `DELETE ... WHERE id IN (...)` over more than
 * that many ids throws rather than deletes, so a v1 install with a four-figure vault would have
 * every prune fail and reimport its whole vault on every unlock. Chunked well under the limit so
 * the split holds regardless of which SQLite build a device is actually running.
 */
private const val PRUNE_CHUNK_SIZE = 500

@Single
internal class LegacyItemRepositoryImpl(
    private val databaseProvider: LegacyDatabaseProvider,
    private val secureElementProbe: LegacySecureElementProbe,
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

        // NotLegacy is the verdict that deletes the file, so it has to rest on evidence rather than
        // on an inference from something going wrong. The probe reads the closed file and answers
        // the only question that proves there is no v1 data in it. `== false` and not `!= true` on
        // purpose: a file the probe could not read at all answers null, and null is not a no.
        if (secureElementProbe.hasSecureElementTable() == false)
            return LegacyDatabaseState.NotLegacy

        // Past that check the provider can only come back empty for a file it could not repair.
        if (databaseProvider.get() == null) return LegacyDatabaseState.Unreadable

        // Room opens the file on the first query rather than when the object is built, so querying
        // is what turns up everything else that can go wrong: a corrupt page, a disk that fills up
        // during the 2-to-3 recreate, a table Room refuses to validate. None of those say the file
        // holds no v1 data, so none of them may end up at NotLegacy.
        return withDao { it.count() }.fold(
            onSuccess = { LegacyDatabaseState.Present },
            onFailure = { LegacyDatabaseState.Unreadable },
        )
    }

    override suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure> = resultBinding {
        // Probed once for the whole run, never once per row. `LegacyCipher.decrypt` folds four
        // different failures into one null, and a gone alias is the only one of them that says
        // nothing in this file is recoverable. Letting it arrive as one Undecryptable per row would
        // tell the user every entry was individually damaged when the entries are intact, and it
        // could not be told apart from a file that really is corrupt end to end.
        //
        // Note this does not gate the 2-to-3 recreate: `state()`'s own `count()` query is the first
        // one against the file and has already run the recreate by the time this is reached. What
        // this probe still buys is not putting the row loop through a key that is already known gone.
        keyProvider.secretKey().asResult(LegacyReadFailure.KeyUnavailable).bind()

        val rows = withDao { it.getAllWithTags() }.bind()

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
        return withDao { dao -> legacyIds.chunked(PRUNE_CHUNK_SIZE).forEach { dao.deleteByIds(it) } }
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

        return try {
            Result.Success(block(dao))
        } catch (e: CancellationException) {
            // Deliberately not `runCatching`, and deliberately unlike the other repositories in
            // this codebase. There a swallowed cancellation becomes a harmless failure; here it
            // becomes a statement about the user's file. A run cancelled because the unlock scope
            // went away tells us nothing about what is in that file, and it must not be able to
            // answer for it. Leave this rethrow where it is.
            throw e
        } catch (_: Exception) {
            Result.Failure(LegacyReadFailure.DatabaseUnreadable)
        }
    }

    private fun LegacyElementWithTags.failure(reason: LegacyFailureReason) =
        LegacyRowFailure(legacyId = element.id, title = element.title, reason = reason)
}
