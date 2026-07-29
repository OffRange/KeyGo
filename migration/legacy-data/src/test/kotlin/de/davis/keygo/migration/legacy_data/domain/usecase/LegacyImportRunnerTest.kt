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
 * die on the way in. Both of those are its behaviour rather than its wiring, so they are tested
 * here rather than guarded by the source-text assertions in `:feature:auth`.
 *
 * `runCurrent` and not `advanceUntilIdle` throughout. The runner launches into a background scope,
 * which is what production does too, and `advanceUntilIdle` returns as soon as the foreground has
 * nothing left rather than running the background work. It would leave every assertion below
 * looking at an import that never started.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LegacyImportRunnerTest {

    private fun TestScope.runnerFor(
        import: RecordingImport,
        diagnostics: MutableList<Pair<String, Throwable?>> = mutableListOf(),
    ) = LegacyImportRunner(
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

        assertEquals(
            1,
            import.invocations,
            "A second unlock during a run must be dropped. Two runs over one legacy file would " +
                "import every row twice, under ids neither run can recognise as the other's.",
        )

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, import.invocations, "The dropped call must not be queued behind the run.")
    }

    @Test
    fun `a call made after a run has finished starts a new run`() = runTest {
        val import = RecordingImport()
        val runner = runnerFor(import)

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(
            2,
            import.invocations,
            "Retrying on every unlock is the point. Dropping a call once a run has finished " +
                "would leave a partial import partial until the next reinstall.",
        )
    }

    @Test
    fun `an import that throws is reported and leaves the next unlock free to retry`() = runTest {
        val boom = IllegalStateException("probing the legacy file blew up")
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { throw boom }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        assertEquals(
            listOf(boom),
            diagnostics.map { it.second },
            "A throw has to be reported. Nothing else in the app is watching this run, so a silent " +
                "one is a user whose data never arrives and a bug report with nothing in it.",
        )

        runner.start()
        runCurrent()

        assertEquals(
            2,
            import.invocations,
            "A failed run must release the runner. Left held, one bad unlock would block the " +
                "import for the life of the install.",
        )
    }

    @Test
    fun `an import that fails with an Error is contained too`() = runTest {
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { throw NoClassDefFoundError("a driver this device lacks") }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        // Not Exception. This runs on an application scope, where anything that gets out takes the
        // process down, and a device missing a native library is not a reason to lose the vault.
        assertIs<NoClassDefFoundError>(diagnostics.single().second)
    }

    @Test
    fun `cancellation is passed through rather than reported as a failure`() = runTest {
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { call ->
            if (call == 1) throw CancellationException("the run was cancelled")
            LegacyMigrationOutcome.NothingToMigrate
        }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        assertTrue(
            diagnostics.isEmpty(),
            "A cancelled run has learned nothing about the user's file and must not answer for it.",
        )

        runner.start()
        runCurrent()

        assertEquals(2, import.invocations, "A cancelled run must release the runner as well.")
    }

    @Test
    fun `a Failed outcome reports its cause through the seam`() = runTest {
        val cause = IllegalStateException("the legacy database exists but could not be opened")
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { LegacyMigrationOutcome.Failed(cause) }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        assertEquals(
            cause,
            diagnostics.singleOrNull()?.second,
            "Failed is this module's real channel for an expected failure, the same way a returned " +
                "Result is everywhere else in this codebase. If it is not routed to the seam, the " +
                "failures that actually happen in the field, like a database that opens but fails " +
                "validation, produce no signal at all.",
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
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { LegacyMigrationOutcome.Migrated(report) }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        assertEquals(
            1,
            diagnostics.size,
            "Individual row skips are the designed behaviour, but a run that silently drops some " +
                "of the user's entries with no trace at all is not acceptable either.",
        )
        assertTrue(
            diagnostics.single().first.contains("Unreadable"),
            "The diagnostic has to carry enough to work out what happened, not just that " +
                "something was skipped.",
        )
        assertTrue(
            !diagnostics.single().first.contains("Gmail"),
            "A row's title is the user's own account name; it must not end up in logcat.",
        )
    }

    @Test
    fun `a Migrated outcome with no row failures but a retained file still reports`() = runTest {
        val report = LegacyMigrationReport(migratedItems = 3, failures = emptyList(), fileRetained = true)
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { LegacyMigrationOutcome.Migrated(report) }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        assertEquals(
            1,
            diagnostics.size,
            "Every row imported cleanly, but the legacy file survived the run. That is exactly the " +
                "ending that duplicates the whole vault on the next unlock, and `hasFailures` alone " +
                "cannot see it: there were none.",
        )
        assertTrue(diagnostics.single().first.contains("retried"))
    }

    @Test
    fun `NothingToMigrate and a clean Migrated produce no diagnostic`() = runTest {
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val import = RecordingImport { call ->
            if (call == 1)
                LegacyMigrationOutcome.NothingToMigrate
            else
                LegacyMigrationOutcome.Migrated(
                    LegacyMigrationReport(migratedItems = 3, failures = emptyList()),
                )
        }
        val runner = runnerFor(import, diagnostics)

        runner.start()
        runCurrent()

        runner.start()
        runCurrent()

        assertEquals(2, import.invocations)
        assertTrue(
            diagnostics.isEmpty(),
            "NothingToMigrate and a Migrated with no row failures are the normal endings; neither " +
                "may produce a diagnostic line.",
        )
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
            "A throwing reporter is a bug in the reporting seam, not in the import. It must not be " +
                "able to do what a throwing import already cannot: take the whole application scope " +
                "down with it.",
        )
    }
}
