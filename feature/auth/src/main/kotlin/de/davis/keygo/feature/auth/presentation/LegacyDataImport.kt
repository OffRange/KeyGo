package de.davis.keygo.feature.auth.presentation

import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long the unlock flow waits for the v1 import before going on without it.
 *
 * There has to be a wait at all because navigating away clears the auth ViewModel and cancels the
 * scope the import runs in, so an import started and left behind would be killed every time and
 * never finish. Given that the wait is real, it needs a ceiling: long enough that no database v1
 * ever wrote comes close to it, short enough that a run which has stopped making progress cannot
 * keep the user out of their own data.
 */
internal val LEGACY_IMPORT_BUDGET: Duration = 30.seconds

/**
 * Runs the v1 import on the way into the app, with the unlock outranking it in every case.
 *
 * The import is the least important thing happening at this moment. Unlock is how the user gets
 * back to their data, so nothing here may stand between them and it: anything thrown is caught, a
 * run that stops making progress is dropped at [budget], and the caller navigates either way. Every
 * one of those endings leaves the legacy file exactly as it was found and gets retried on the next
 * unlock, which is what makes giving up here cost the user nothing.
 *
 * `MigrateLegacyDataUseCase` reports the failures it can see as [LegacyMigrationOutcome.Failed], but
 * it can still throw. It probes the file and deletes a non-legacy one outside its own catch, and it
 * rethrows cancellation on purpose. Calling it bare from `viewModelScope.launch` would let any of
 * that skip the navigation that follows, leaving a user whose session is already live stuck on the
 * lock screen.
 *
 * [Throwable] and not [Exception], because not every way this goes wrong is an exception. A module
 * that reaches Room, a native SQLite driver and the Keystore can raise a [LinkageError] or a
 * [NoClassDefFoundError] on a device missing something it expected, and none of that is a reason to
 * refuse the user their vault.
 *
 * Cancellation is passed through rather than swallowed. A cancelled scope means this screen is
 * already going away, so there is no navigation left to protect.
 *
 * The [budget] is best effort. It can only end a run at a suspension point, so a call that blocks
 * its thread outright still holds the unlock for as long as it blocks.
 *
 * @return what the import did, or null if it threw or ran out of budget. The unlock path ignores it
 * on purpose. A legacy file that exists but cannot be opened is left alone rather than deleted on a
 * guess, so it reports failure on every single unlock, forever, and turning that into something the
 * user has to dismiss would make one unfixable problem into a second one.
 */
internal suspend fun importLegacyData(
    budget: Duration = LEGACY_IMPORT_BUDGET,
    migrate: suspend () -> LegacyMigrationOutcome,
): LegacyMigrationOutcome? = try {
    withTimeoutOrNull(budget) { migrate() }
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    null
}
