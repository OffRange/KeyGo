package de.davis.keygo.migration.legacy_data.domain.usecase

import android.util.Log
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "LegacyDataImport"

/**
 * Starts the v1 import in the background and returns at once.
 *
 * Everything that calls this is a session start, so the caller is the unlock the user is waiting
 * on. The import is not work that can sit in front of them: it opens the inherited file, decrypts
 * every row through the Keystore and writes them all back under new keys, and how long that takes
 * is a property of the user's old database rather than anything we can bound. It runs on its own
 * application scope on [Dispatchers.IO], off the main thread and out of the caller's lifetime,
 * because navigating away from the auth screen clears its ViewModel a moment after this returns.
 *
 * There is no in-process lock today for a run to be interrupted by: the session lives for the
 * process, and the app's only current "lock" is a process or activity restart, which ends the run
 * along with everything else. A run interrupted by a future lock feature would fail instead, leave
 * the legacy file exactly as it found it, and be retried on the next unlock.
 */
@Single
class StartLegacyDataImportUseCase internal constructor(
    migrateLegacyData: MigrateLegacyDataUseCase,
) {

    private val runner = LegacyImportRunner(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        report = { message, cause ->
            if (cause != null) Log.e(TAG, message, cause) else Log.e(TAG, message)
        },
        import = { migrateLegacyData() },
    )

    operator fun invoke() = runner.start()
}

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
 * skipping a bad row is the designed behaviour. [LegacyMigrationOutcome.NothingToMigrate] and a
 * [LegacyMigrationOutcome.Migrated] with no row failures are the normal endings and report nothing.
 *
 * Nothing thrown may reach [scope], where an uncaught throwable would take the process down. That
 * covers [import] itself and also [report]: a throwing reporting implementation must not be able to
 * do what a throwing import already cannot. [Throwable] and not [Exception] for [import]:
 * `MigrateLegacyDataUseCase` catches around its own migrate call, but not around the state probe it
 * switches on or the deletion it does for a file that turns out not to be v1's, and a module
 * reaching Room, a native SQLite driver and the Keystore can raise a [LinkageError] or a
 * [NoClassDefFoundError] on a device missing something it expected.
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

            is LegacyMigrationOutcome.Migrated -> if (outcome.report.hasFailures)
                reportSafely(rowFailureSummary(outcome.report), null)

            LegacyMigrationOutcome.NothingToMigrate -> Unit
        }
    }

    private fun rowFailureSummary(migrationReport: LegacyMigrationReport): String {
        // Grouped by reason rather than by row: a row's title is the user's own account name, and
        // logcat is not the place for it. Counts and reasons are enough to work out what happened.
        val byReason = migrationReport.failures.groupingBy { it.reason }.eachCount().entries
            .joinToString { (reason, count) -> "$reason=$count" }
        val total = migrationReport.migratedItems + migrationReport.failures.size

        return "v1 import finished with ${migrationReport.failures.size} of $total row(s) " +
            "skipped: $byReason"
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
