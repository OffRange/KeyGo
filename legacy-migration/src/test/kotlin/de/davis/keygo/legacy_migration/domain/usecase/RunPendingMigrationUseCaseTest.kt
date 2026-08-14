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

    @Test
    fun `reports the rows that were skipped and still clears the marker`() = runTest {
        val result = useCase { migrated(failures = listOf(rowFailure(1), rowFailure(2))) }()

        assertEquals(MigrationResult.Completed(skippedItems = 2), result)
        assertEquals("", mainPasswordRepository.hash)
    }

    /**
     * A file left behind by a failed prune or a failed delete, rather than by a failed row. The
     * rows are already in v2, so a retry would duplicate them; the marker goes and the stale file
     * is accepted. This is the one place the design retries less than the code it replaced, and it
     * is pinned here so the trade is deliberate rather than discovered.
     */
    @Test
    fun `clears the marker even when the legacy file could not be deleted`() = runTest {
        val result = useCase { migrated(fileRetained = true) }()

        assertEquals(MigrationResult.Completed(skippedItems = 0), result)
        assertEquals("", mainPasswordRepository.hash)
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
