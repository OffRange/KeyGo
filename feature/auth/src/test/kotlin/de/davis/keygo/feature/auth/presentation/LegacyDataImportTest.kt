package de.davis.keygo.feature.auth.presentation

import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The unlock flow waits for the import before it navigates, because navigating clears the auth
 * ViewModel and cancels the scope the import runs in. That wait is what these tests are about: it
 * has to end, whatever the import does, or a user who can no longer be let into their own data is
 * the price of an optional one-off copy.
 */
class LegacyDataImportTest {

    @Test
    fun `an outcome the import reports is handed straight back`() = runTest {
        val outcome = importLegacyData { LegacyMigrationOutcome.NothingToMigrate }

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, outcome)
    }

    @Test
    fun `a reported failure is not treated as a reason to stop`() = runTest {
        val outcome = importLegacyData {
            LegacyMigrationOutcome.Failed(IllegalStateException("unreadable legacy file"))
        }

        assertTrue(outcome is LegacyMigrationOutcome.Failed)
    }

    @Test
    fun `an import that throws does not reach the unlock flow`() = runTest {
        val outcome = importLegacyData { throw IllegalStateException("probing the file blew up") }

        assertNull(outcome)
    }

    @Test
    fun `an import that throws an Error does not reach the unlock flow either`() = runTest {
        val outcome = importLegacyData { throw StackOverflowError("deep in Room") }

        assertNull(outcome)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an import that never finishes is abandoned once the budget runs out`() = runTest {
        val outcome = importLegacyData { awaitCancellation() }

        assertNull(outcome)
        assertEquals(LEGACY_IMPORT_BUDGET.inWholeMilliseconds, testScheduler.currentTime)
    }

    @Test
    fun `an import that finishes inside the budget is waited for`() = runTest {
        var finished = false
        val outcome = importLegacyData(budget = 10.seconds) {
            delay(9.seconds)
            finished = true
            LegacyMigrationOutcome.NothingToMigrate
        }

        assertTrue(finished)
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, outcome)
    }

    /**
     * A cancelled scope means the auth screen is already going away, so there is no navigation left
     * to protect and nothing to learn about the user's file. Swallowing it here would break
     * structured concurrency and undo the rethrow the migration module deliberately keeps.
     */
    @Test
    fun `cancellation is passed through rather than swallowed`() = runTest {
        assertFailsWith<CancellationException> {
            importLegacyData { throw CancellationException("the unlock scope went away") }
        }
    }
}
