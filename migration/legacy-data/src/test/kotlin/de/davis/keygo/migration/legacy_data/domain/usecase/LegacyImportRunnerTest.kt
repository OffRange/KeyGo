package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationReport
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Stands in for the import. Counts its calls and takes the call number, so a test can make the
 * first run behave differently from the one that follows it.
 */
private class RecordingImport(
    private val body: suspend (call: Int) -> LegacyMigrationOutcome = {
        LegacyMigrationOutcome.NothingToMigrate
    },
) {
    var invocations = 0
        private set

    suspend operator fun invoke(): LegacyMigrationOutcome {
        invocations++
        return body(invocations)
    }
}

/**
 * The runner is the only thing standing between a second unlock and a second copy of the user's
 * vault, and the only thing standing between a throw in the import and a process the user watches
 * die on the way in.
 *
 * Two runs over one legacy file would import every row twice under ids neither run can recognise as
 * the other's, which is why a call during a run is dropped. A run that finished with rows still on
 * disk must release, because retrying on the next unlock is what turns a partial import into a
 * complete one; a run that finished with nothing left must not, because every later unlock would
 * pay to conclude the same nothing.
 *
 * `runCurrent` and not `advanceUntilIdle` throughout. The runner launches into a background scope,
 * which is what production does too, and `advanceUntilIdle` returns as soon as the foreground has
 * nothing left rather than running the background work. It would leave every assertion below
 * looking at an import that never started.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LegacyImportRunnerTest {

    private val diagnostics = mutableListOf<Pair<String, Throwable?>>()

    private val clearedFile = LegacyMigrationOutcome.Migrated(
        LegacyMigrationReport(migratedItems = 3, failures = emptyList()),
    )

    private val retainedFile = LegacyMigrationOutcome.Migrated(
        LegacyMigrationReport(migratedItems = 3, failures = emptyList(), fileRetained = true),
    )

    private fun TestScope.runnerFor(import: RecordingImport) = LegacyImportRunner(
        scope = backgroundScope,
        report = { message, cause -> diagnostics += message to cause },
        import = { import() },
    )

    @Test
    fun `a call made while a run is in flight is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val import = RecordingImport {
            gate.await()
            LegacyMigrationOutcome.NothingToMigrate
        }
        val runner = runnerFor(import)

        runner.start()
        runCurrent() // the first run gets going and parks on the gate

        runner.start()
        runCurrent()

        assertEquals(1, import.invocations, "a second unlock during a run must be dropped")

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, import.invocations, "the dropped call must not be queued behind the run")
    }

    @Test
    fun `a call made after a run that left rows behind starts a new run`() = runTest {
        val import = RecordingImport { retainedFile }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(2, import.invocations, "a finished run must leave the next unlock free")
    }

    /**
     * The verdict that ends the retry. Almost every install never had a v1 file, and without this
     * each later unlock would re-open it, count it and sweep the filesystem to reach the same
     * nothing. Unlocks are not rare either: the autofill service and both passkey activities start
     * sessions of their own.
     */
    @Test
    fun `a run with nothing left to import is not repeated`() = runTest {
        val import = RecordingImport { LegacyMigrationOutcome.NothingToMigrate }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(1, import.invocations, "a verdict of nothing to import holds for the process")
    }

    @Test
    fun `a run that cleared the legacy file is not repeated either`() = runTest {
        val import = RecordingImport { clearedFile }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(1, import.invocations, "the file is gone; a retry has nothing left to find")
    }

    @Test
    fun `an import that throws is reported and leaves the next unlock free to retry`() = runTest {
        val boom = IllegalStateException("probing the legacy file blew up")
        val import = RecordingImport { throw boom }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        assertEquals(listOf(boom), diagnostics.map { it.second }, "a throw must be reported")

        runner.start()
        runCurrent()

        assertEquals(2, import.invocations, "a failed run must release the runner")
    }

    @Test
    fun `an import that fails with an Error is contained too`() = runTest {
        val import = RecordingImport { throw NoClassDefFoundError("a driver this device lacks") }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        // Not Exception. This runs on an application scope, where anything that gets out takes the
        // process down, and a device missing a native library is not a reason to lose the vault.
        assertIs<NoClassDefFoundError>(diagnostics.single().second)
    }

    @Test
    fun `cancellation is passed through rather than reported as a failure`() = runTest {
        val import = RecordingImport { call ->
            if (call == 1) throw CancellationException("the run was cancelled")
            LegacyMigrationOutcome.NothingToMigrate
        }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        assertTrue(
            diagnostics.isEmpty(),
            "a cancelled run has learned nothing about the user's file and must not answer for it",
        )

        runner.start()
        runCurrent()

        assertEquals(2, import.invocations, "a cancelled run must release the runner as well")
    }

    @Test
    fun `a Failed outcome reports its cause through the seam`() = runTest {
        val cause = IllegalStateException("the legacy database exists but could not be opened")
        val import = RecordingImport { LegacyMigrationOutcome.Failed(cause) }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        assertEquals(
            cause,
            diagnostics.singleOrNull()?.second,
            "Failed is this module's channel for an expected failure and must reach the seam",
        )
    }

    @Test
    fun `a Migrated outcome with row failures produces a diagnostic`() = runTest {
        val report = LegacyMigrationReport(
            migratedItems = 2,
            failures = listOf(
                LegacyRowFailure(
                    legacyId = 1L,
                    title = "Gmail",
                    reason = LegacyFailureReason.Unreadable,
                ),
            ),
        )
        val import = RecordingImport { LegacyMigrationOutcome.Migrated(report) }
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        assertEquals(1, diagnostics.size, "a run that drops entries must leave a trace")
        assertTrue(
            diagnostics.single().first.contains("Unreadable"),
            "the diagnostic must carry the reason, not just that something was skipped",
        )
        assertTrue(
            !diagnostics.single().first.contains("Gmail"),
            "a row's title is the user's own account name; it must not end up in logcat",
        )
    }

    @Test
    fun `a Migrated outcome with no row failures but a retained file still reports`() = runTest {
        val runner = runnerFor(RecordingImport { retainedFile })

        runner.start()
        runCurrent()

        // The ending that duplicates the whole vault on the next unlock, and `hasFailures` alone
        // cannot see it: there were none.
        assertEquals(1, diagnostics.size, "a retained file must report even with no row failures")
        assertTrue(diagnostics.single().first.contains("retried"))
    }

    /** A runner each, because both of these endings latch and so cannot follow one another. */
    @Test
    fun `NothingToMigrate and a clean Migrated produce no diagnostic`() = runTest {
        runnerFor(RecordingImport { LegacyMigrationOutcome.NothingToMigrate }).start()
        runCurrent()

        runnerFor(RecordingImport { clearedFile }).start()
        runCurrent()

        assertTrue(diagnostics.isEmpty(), "the normal endings must produce no diagnostic line")
    }

    @Test
    fun `a reporter that throws is contained, and the runner still releases`() = runTest {
        val cause = IllegalStateException("the legacy database exists but could not be opened")
        val import = RecordingImport { LegacyMigrationOutcome.Failed(cause) }
        val runner = LegacyImportRunner(
            scope = backgroundScope,
            report = { _, _ -> throw RuntimeException("the reporter itself is broken") },
            import = { import() },
        )

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(
            2,
            import.invocations,
            "a throwing reporter must not take the application scope down",
        )
    }
}
