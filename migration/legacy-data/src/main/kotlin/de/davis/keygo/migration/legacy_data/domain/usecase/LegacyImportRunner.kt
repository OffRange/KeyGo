package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [import] on [scope], one run at a time, with nothing escaping into [scope].
 *
 * One run at a time is what stops two unlocks from copying the same rows twice. The import mints a
 * fresh item id for every row it takes across and has no key to recognise a row it already
 * imported, so two runs over one legacy file would leave the user holding two of everything. A call
 * made while a run is in flight is dropped rather than queued, and a call made after one has
 * finished starts a new run, which is what makes the import retry on every unlock.
 *
 * [import] returns its outcome rather than throwing for anything expected, and every ending that is
 * not a clean success is turned into one call to [report]. [LegacyMigrationOutcome.Failed] reports
 * its cause directly. A [LegacyMigrationOutcome.Migrated] whose report carries row failures also
 * reports, because a run that silently drops some of the user's entries needs a trace even though
 * skipping a bad row is the designed behaviour. So does one whose file could not be cleared even
 * though every row it looked at imported cleanly: that run will reimport the whole vault on the
 * next unlock, and that is exactly the kind of ending "no row failures" must not be allowed to
 * paper over. [LegacyMigrationOutcome.NothingToMigrate] and a [LegacyMigrationOutcome.Migrated]
 * with no row failures and no file left behind are the only endings that report nothing.
 *
 * Nothing thrown may reach [scope], where an uncaught throwable would take the process down. That
 * covers [import] itself and also [report]: a throwing reporting implementation must not be able to
 * do what a throwing import already cannot. [Throwable] and not [Exception] for [import]:
 * [MigrateLegacyDataUseCase] catches [Exception] around its whole run, which leaves everything that
 * is not one uncaught, and a module reaching Room, a native SQLite driver and the Keystore can raise
 * a [LinkageError] or a [NoClassDefFoundError] on a device missing something it expected.
 *
 * Cancellation is rethrown rather than reported. A run cut short has learned nothing about the
 * user's file and must not get to answer for it, and swallowing it would undo the rethrow the
 * migration keeps on purpose.
 */
internal class LegacyImportRunner(
    private val scope: CoroutineScope,
    private val report: (message: String, cause: Throwable?) -> Unit,
    private val import: suspend () -> LegacyMigrationOutcome,
) {

    private val inFlight = AtomicBoolean(false)

    fun start() {
        if (!inFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                reportOutcome(import())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportSafely("v1 import threw", e)
            }
            // Released on completion rather than in a finally, so a run whose scope died before its
            // body ever ran still gives the next unlock its turn.
        }.invokeOnCompletion { inFlight.set(false) }
    }

    private fun reportOutcome(outcome: LegacyMigrationOutcome) {
        when (outcome) {
            is LegacyMigrationOutcome.Failed ->
                reportSafely("v1 import failed, retrying on the next unlock", outcome.cause)

            is LegacyMigrationOutcome.Migrated -> if (
                outcome.report.hasFailures || outcome.report.fileRetained
            )
                reportSafely(migrationSummary(outcome.report), null)

            LegacyMigrationOutcome.NothingToMigrate -> Unit
        }
    }

    private fun migrationSummary(migrationReport: LegacyMigrationReport): String {
        val parts = mutableListOf<String>()

        if (migrationReport.hasFailures) {
            // Grouped by reason rather than by row: a row's title is the user's own account name,
            // and logcat is not the place for it. Counts and reasons are enough to work out what
            // happened.
            val byReason = migrationReport.failures.groupingBy { it.reason }.eachCount().entries
                .joinToString { (reason, count) -> "$reason=$count" }
            val total = migrationReport.migratedItems + migrationReport.failures.size
            parts += "${migrationReport.failures.size} of $total row(s) skipped: $byReason"
        }

        if (migrationReport.fileRetained)
            parts += "the legacy file could not be cleared and will be retried on the next unlock"

        return "v1 import finished: ${parts.joinToString("; ")}"
    }

    /**
     * Calls [report] without letting it reach [scope]. [report] is a reporting seam and not part of
     * the import, so a throw out of it must be contained exactly like a throw out of [import] is.
     */
    private fun reportSafely(message: String, cause: Throwable?) {
        try {
            report(message, cause)
        } catch (_: Throwable) {
            // Nothing to escalate to: this is already the containment boundary.
        }
    }
}
