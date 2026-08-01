package de.davis.keygo.migration.legacy_data.domain.usecase

import android.util.Log
import de.davis.keygo.core.security.domain.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single

private const val TAG = "LegacyDataImport"

/**
 * Runs the v1 import in the background on every unlock.
 *
 * Driven by [Session.sessionStarts] rather than called from the auth screen, because the auth
 * screen is only one of the doors: the autofill service and both passkey activities start sessions
 * of their own. An import wired to some of them would leave a partial migration sitting until the
 * user happened to come back through the right one, and would go quietly stale again the next time
 * a door is added.
 *
 * The import is not work that can sit in front of the user: it opens the inherited file, decrypts
 * every row through the Keystore and writes them all back under new keys, and how long that takes
 * is a property of the user's old database rather than anything we can bound. It runs on its own
 * application scope on [Dispatchers.IO], off the main thread and outside the lifetime of whatever
 * unlocked, which for the auth screen is a ViewModel cleared moments later.
 *
 * There is no in-process lock today for a run to be interrupted by: the session lives for the
 * process, and the app's only current "lock" is a process or activity restart, which ends the run
 * along with everything else. A run interrupted by a future lock feature would fail instead, leave
 * the legacy file exactly as it found it, and be retried on the next unlock.
 *
 * Created at Koin start so the collector is already listening when the first session begins.
 */
@Single(createdAtStart = true)
internal class LegacyDataImportStarter(
    session: Session,
    migrateLegacyData: MigrateLegacyDataUseCase,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runner = LegacyImportRunner(
        scope = scope,
        report = { message, cause ->
            if (cause != null) Log.e(TAG, message, cause) else Log.e(TAG, message)
        },
        import = { migrateLegacyData() },
    )

    init {
        session.sessionStarts.onEach { runner.start() }.launchIn(scope)
    }
}
