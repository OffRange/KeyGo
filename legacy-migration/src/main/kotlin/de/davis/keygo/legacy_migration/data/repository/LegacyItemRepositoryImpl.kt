package de.davis.keygo.legacy_migration.data.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.legacy_migration.data.json.LegacyDetailParser
import de.davis.keygo.legacy_migration.data.local.dao.LegacyElementDao
import de.davis.keygo.legacy_migration.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.legacy_migration.data.local.pojo.LegacyElementWithTags
import de.davis.keygo.legacy_migration.data.mapper.toLegacyItem
import de.davis.keygo.legacy_migration.domain.crypto.LegacyCipher
import de.davis.keygo.legacy_migration.domain.model.LegacyFailureReason
import de.davis.keygo.legacy_migration.domain.model.LegacyItem
import de.davis.keygo.legacy_migration.domain.model.LegacyReadFailure
import de.davis.keygo.legacy_migration.domain.model.LegacyRowFailure
import de.davis.keygo.legacy_migration.domain.repository.LegacyItemRepository
import de.davis.keygo.legacy_migration.domain.repository.LegacyKeyRepository
import de.davis.keygo.legacy_migration.domain.repository.LegacyReadResult
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
    private val keyRepository: LegacyKeyRepository,
    private val cipher: LegacyCipher,
    private val parser: LegacyDetailParser,
) : LegacyItemRepository {

    override suspend fun readAll(): Result<LegacyReadResult, LegacyReadFailure> = resultBinding {
        // Asked first, and it is what decides whether the file is deleted. Zero is the destructive
        // verdict, so it rests on a count that was actually taken: a file that will not open fails
        // as DatabaseUnreadable here and never reaches the comparison.
        (remainingCount().bind() > 0).asResult(LegacyReadFailure.DatabaseEmpty).bind()

        // Resolved once for the whole run rather than inferred from a run of nulls; see
        // [LegacyReadFailure.KeyUnavailable]. This does not gate the 2-to-3 recreate, which the
        // count above already triggered as the first query against the file. What it still buys is
        // not putting the row loop through a key that is already known gone, and one Keystore round
        // trip for the run instead of one per blob.
        val key = keyRepository.secretKey().asResult(LegacyReadFailure.KeyUnavailable).bind()

        val rows = withDao { it.getAllWithTags() }.bind()

        val items = mutableListOf<LegacyItem>()
        val failures = mutableListOf<LegacyRowFailure>()

        for (row in rows) {
            val detail = cipher.decrypt(row.element.data, key)?.let(parser::parse)
            if (detail == null) {
                failures += row.failure(LegacyFailureReason.Unreadable)
                continue
            }

            items += row.toLegacyItem(detail)
        }

        LegacyReadResult(items = items, failures = failures, legacyKey = key)
    }

    override suspend fun prune(legacyIds: List<Long>): Result<Unit, LegacyReadFailure> {
        if (legacyIds.isEmpty()) return Result.Success(Unit)
        return withDao { dao ->
            legacyIds.chunked(PRUNE_CHUNK_SIZE).forEach { dao.deleteByIds(it) }
        }
    }

    override suspend fun remainingCount(): Result<Int, LegacyReadFailure> = withDao { it.count() }

    override fun deleteDatabase(): Boolean = databaseProvider.delete()

    /**
     * Runs [block] against the legacy DAO, turning the ways the file can refuse to be read into a
     * failure rather than a raw throw.
     *
     * The two are not the same failure. No provider means no file to open at all, which is a clean
     * install and holds nothing to lose, so it answers [LegacyReadFailure.DatabaseEmpty]. A file
     * that is there but that Room could not validate on the first query answers
     * [LegacyReadFailure.DatabaseUnreadable] and is left standing.
     */
    private suspend fun <T> withDao(
        block: suspend (LegacyElementDao) -> T,
    ): Result<T, LegacyReadFailure> {
        val dao = databaseProvider.get()?.legacyElementDao()
            ?: return Result.Failure(LegacyReadFailure.DatabaseEmpty)

        return try {
            Result.Success(block(dao))
        } catch (e: CancellationException) {
            // Not swallowed into a failure the way the other repositories do it. There a swallowed
            // cancellation is harmless; here it would become a statement about the user's file. A
            // run cancelled because the unlock scope went away tells us nothing about what is in
            // that file, and it must not be able to answer for it.
            throw e
        } catch (_: Exception) {
            Result.Failure(LegacyReadFailure.DatabaseUnreadable)
        }
    }

    private fun LegacyElementWithTags.failure(reason: LegacyFailureReason) =
        LegacyRowFailure(legacyId = element.id, title = element.title, reason = reason)
}
