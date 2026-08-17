package de.davis.keygo.legacy_migration.domain.usecase

import de.davis.keygo.legacy_migration.FakeMainPasswordRepository
import de.davis.keygo.legacy_migration.clearMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.model.LegacyFailureReason
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationOutcome
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationReport
import de.davis.keygo.legacy_migration.domain.model.LegacyRowFailure
import de.davis.keygo.legacy_migration.domain.model.MigrationResult
import de.davis.keygo.legacy_migration.hasMainPasswordUseCase
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunPendingMigrationUseCaseTest {

    private val mainPasswordRepository = FakeMainPasswordRepository(hash = "a-v1-bcrypt-hash")

    private var importsRun = 0

    private fun useCase(importer: LegacyDataImporter) = RunPendingMigrationUseCase(
        hasMainPassword = hasMainPasswordUseCase(mainPasswordRepository),
        importLegacyData = LegacyDataImporter {
            importsRun++
            importer()
        },
        clearMainPassword = clearMainPasswordUseCase(mainPasswordRepository),
    )

    private fun migrated(failures: List<LegacyRowFailure> = emptyList(), fileRetained: Boolean = false) =
        LegacyMigrationOutcome.Migrated(
            LegacyMigrationReport(
                migratedItems = 3,
                failures = failures,
                fileRetained = fileRetained,
            ),
        )

    private fun rowFailure(id: Long) = LegacyRowFailure(
        legacyId = id,
        title = "an account",
        reason = LegacyFailureReason.Unreadable,
    )

    @Test
    fun `clears the marker once the import reports rows migrated`() = runTest {
        val result = useCase { migrated() }()

        assertEquals(MigrationResult.Completed(skippedItems = 0), result)
        assertEquals("", mainPasswordRepository.hash)
    }

    @Test
    fun `clears the marker when there was nothing to migrate`() = runTest {
        val result = useCase { LegacyMigrationOutcome.NothingToMigrate }()

        assertEquals(MigrationResult.Completed(skippedItems = 0), result)
        assertEquals("", mainPasswordRepository.hash)
    }

    /**
     * The marker follows the file, not the row count. A report that names failures and still says
     * the file is gone has nothing left to come back to, so counting the skipped rows for the
     * screen and dropping the credential are not in conflict.
     */
    @Test
    fun `reports the rows that were skipped and still clears the marker`() = runTest {
        val result = useCase { migrated(failures = listOf(rowFailure(1), rowFailure(2))) }()

        assertEquals(MigrationResult.Completed(skippedItems = 2), result)
        assertEquals("", mainPasswordRepository.hash)
    }

    /**
     * A file left behind by a failed prune or a failed delete, rather than by a failed row. Nothing
     * on screen distinguishes it - the run reached a verdict and the user is let through - but the
     * marker has to survive it: it is the only thing that brings a later run back to a
     * secure_element_database that is still on disk and still decryptable by an alias only that run
     * can delete. Clearing it here made that file permanent.
     *
     * The trade is a prune that keeps failing over rows already in v2, which reimports them on the
     * next unlock. A duplicate is visible and undoable; a retained v1 database is neither.
     */
    @Test
    fun `keeps the marker when the legacy file could not be deleted`() = runTest {
        val result = useCase { migrated(fileRetained = true) }()

        assertEquals(MigrationResult.Completed(skippedItems = 0), result)
        assertEquals("a-v1-bcrypt-hash", mainPasswordRepository.hash)
    }

    /**
     * The realistic shape of a skipped row: `MigrateLegacyDataUseCase` never deletes a file it
     * could not empty, so failures and a retained file arrive together. The rows it could not read
     * are still there, and the marker is what lets a later run try them again.
     */
    @Test
    fun `keeps the marker when rows were skipped and the file stayed behind`() = runTest {
        val result = useCase {
            migrated(failures = listOf(rowFailure(1), rowFailure(2)), fileRetained = true)
        }()

        assertEquals(MigrationResult.Completed(skippedItems = 2), result)
        assertEquals("a-v1-bcrypt-hash", mainPasswordRepository.hash)
    }

    /**
     * The invariant the whole module exists to hold. A run that could not reach a verdict about the
     * v1 file must leave the credential that gets the user back to it.
     */
    @Test
    fun `keeps the marker when the import failed`() = runTest {
        val cause = IllegalStateException("could not read the legacy database")

        val result = useCase { LegacyMigrationOutcome.Failed(cause) }()

        assertIs<MigrationResult.Incomplete>(result)
        assertEquals(cause, result.cause)
        assertEquals("a-v1-bcrypt-hash", mainPasswordRepository.hash)
    }

    /**
     * The short circuit that keeps a clean install from opening the legacy path at all. Before this
     * existed, every unlock on every install swept it once per process.
     */
    @Test
    fun `never touches the import when there is no marker`() = runTest {
        mainPasswordRepository.hash = ""

        val result = useCase { migrated() }()

        assertEquals(MigrationResult.NotPending, result)
        assertEquals(0, importsRun)
    }

    /**
     * MigrateLegacyDataUseCase catches Exception, which leaves everything that is not one uncaught.
     * A module reaching Room, a native SQLite driver and the Keystore can raise a LinkageError on a
     * device missing something it expected, and this now runs in viewModelScope where that would
     * take the process down rather than being logged.
     */
    @Test
    fun `contains a throwable that is not an exception`() = runTest {
        val result = useCase { throw NoClassDefFoundError("libsqlite") }()

        assertIs<MigrationResult.Incomplete>(result)
        assertTrue(result.cause is NoClassDefFoundError)
        assertEquals("a-v1-bcrypt-hash", mainPasswordRepository.hash)
    }

    @Test
    fun `lets cancellation through rather than reporting it as a verdict`() = runTest {
        assertFailsWith<CancellationException> {
            useCase { throw CancellationException("unlock scope went away") }()
        }

        assertEquals("a-v1-bcrypt-hash", mainPasswordRepository.hash)
    }
}
