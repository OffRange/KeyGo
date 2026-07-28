package de.davis.keygo.migration.legacy_data.domain.usecase

import android.util.Log
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
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
 * The trade-off that buys: a run still going when the user locks the vault will fail, because the
 * session holds the key material it needs. That costs nothing. A failed run leaves the legacy file
 * exactly as it found it, and the next unlock starts a fresh one.
 */
@Single
class StartLegacyDataImportUseCase internal constructor(
    migrateLegacyData: MigrateLegacyDataUseCase,
) {

    private val runner = LegacyImportRunner(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        onFailure = { Log.e(TAG, "v1 import failed, retrying on the next unlock", it) },
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
 * Nothing thrown may reach [scope], where an uncaught throwable would take the process down.
 * [Throwable] and not [Exception]: `MigrateLegacyDataUseCase` catches around its own migrate call,
 * but not around the state probe it switches on or the deletion it does for a file that turns out
 * not to be v1's, and a module reaching Room, a native SQLite driver and the Keystore can raise a
 * [LinkageError] or a [NoClassDefFoundError] on a device missing something it expected.
 *
 * Cancellation is rethrown rather than reported. A run cut short has learned nothing about the
 * user's file and must not get to answer for it, and swallowing it would undo the rethrow the
 * migration keeps on purpose.
 */
internal class LegacyImportRunner(
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit,
    private val import: suspend () -> LegacyMigrationOutcome,
) {

    private val inFlight = AtomicBoolean(false)

    fun start() {
        if (!inFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                import()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onFailure(e)
            }
            // Released on completion rather than in a finally, so a run whose scope died before its
            // body ever ran still gives the next unlock its turn.
        }.invokeOnCompletion { inFlight.set(false) }
    }
}
