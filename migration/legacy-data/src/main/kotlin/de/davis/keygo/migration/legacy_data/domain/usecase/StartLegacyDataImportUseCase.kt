package de.davis.keygo.migration.legacy_data.domain.usecase

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single

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
