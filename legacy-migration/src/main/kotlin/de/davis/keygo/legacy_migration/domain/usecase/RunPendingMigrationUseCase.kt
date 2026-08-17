package de.davis.keygo.legacy_migration.domain.usecase

import android.util.Log
import de.davis.keygo.legacy_migration.di.annotation.MigrationScopeQualifier
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationOutcome
import de.davis.keygo.legacy_migration.domain.model.MigrationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs whatever is left of the v1 migration, in order, once a session is live.
 *
 * The v1 main password record is the marker for the whole migration, not just for the access half.
 * It is read first, so an install that never ran v1 never opens the legacy path at all, and it is
 * cleared last, and only once the import has said a later run would find nothing left. Between
 * those two the user's credential is the only way back to a migration that did not finish, so
 * dropping it early would strand them with rows still on disk and nothing to act on.
 *
 * Called after every path that establishes a session on the auth screen, which is the only door a
 * migrating install has: both unlock paths need an account that does not exist yet while the
 * migration is pending, so the autofill service and the passkey activities cannot reach this.
 */
@Single
class RunPendingMigrationUseCase internal constructor(
    private val hasMainPassword: HasMainPasswordUseCase,
    private val importLegacyData: LegacyDataImporter,
    private val clearMainPassword: ClearMainPasswordUseCase,

    @param:MigrationScopeQualifier
    private val scope: CoroutineScope,
) {

    private val lock = Mutex()
    private var inFlight: Deferred<MigrationResult>? = null

    suspend operator fun invoke(): MigrationResult = currentRun().await()

    private suspend fun currentRun(): Deferred<MigrationResult> = lock.withLock {
        inFlight?.takeIf { it.isActive }
            ?: scope.async { runPending() }.also { inFlight = it }
    }

    private suspend fun runPending(): MigrationResult {
        if (!hasMainPassword()) return MigrationResult.NotPending

        val outcome = try {
            importLegacyData()
        } catch (e: CancellationException) {
            // A run cancelled because the scope went away tells us nothing about the user's file,
            // and it must not be able to answer for it. See LegacyItemRepositoryImpl.withDao.
            throw e
        } catch (e: Throwable) {
            // Throwable and not Exception: MigrateLegacyDataUseCase catches Exception around its
            // whole run, which leaves everything that is not one uncaught, and a module reaching
            // Room, a native SQLite driver and the Keystore can raise a LinkageError or a
            // NoClassDefFoundError on a device missing something it expected. Uncaught, that would
            // surface as a crash at whoever joined the run rather than as a migration that failed.
            return incomplete(e)
        }

        return when (outcome) {
            is LegacyMigrationOutcome.Failed -> incomplete(outcome.cause)

            // Exhaustive rather than an else, so an outcome added later cannot default into the
            // branch that drops the user's v1 credential.
            LegacyMigrationOutcome.NothingToMigrate -> {
                clearMainPassword()
                MigrationResult.Completed(skippedItems = 0)
            }

            is LegacyMigrationOutcome.Migrated -> {
                // A retained file is a file with v1 rows still in it, and the marker is the only
                // thing that brings anything back to them: clearing it here would leave
                // secure_element_database on disk forever, decryptable by an alias no later run
                // can reach to delete either. So it goes only once a later run would find nothing.
                //
                // The cost is a prune that keeps failing while the rows are already in v2, which
                // reimports them on every unlock. That is a duplicate the user can see and undo;
                // the alternative is a v1 database they cannot.
                if (outcome.nothingLeftToImport) clearMainPassword()
                MigrationResult.Completed(skippedItems = outcome.report.failures.size)
            }
        }
    }

    private fun incomplete(cause: Throwable): MigrationResult.Incomplete {
        Log.e(TAG, "v1 import did not finish", cause)
        return MigrationResult.Incomplete(cause)
    }

    private companion object {
        const val TAG = "RunPendingMigration"
    }
}
