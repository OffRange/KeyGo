package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import de.davis.keygo.feature.backup.domain.usecase.CleanupBackupResourcesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Sweeps the ARK escrow once per process start.
 *
 * The escrow is only meant to outlive the user's session for as long as a job is actually
 * scheduled, so something has to notice when the platform drops that job without ever running it.
 * A finished run cannot notice: in that case there is none. Process start is the last trigger left
 * that does not wait on the user navigating somewhere, which is why this hangs off startup rather
 * than off the backup screen - a user who schedules a backup and never opens the feature again
 * would otherwise keep the escrow alive indefinitely.
 *
 * [CleanupBackupResourcesUseCase.reconcile] needs no session and no ARK, so running it this early
 * is safe. It is fire-and-forget: nothing on screen depends on the result, and a failure only
 * means the next start tries again.
 */
@Single(createdAtStart = true)
internal class BackupEscrowReconciler(
    cleanupBackupResources: CleanupBackupResourcesUseCase,
    @AppScopeQualifier appScope: CoroutineScope,
) {

    init {
        // Dispatchers.IO rather than the app scope's default: reconcile reads DataStore and the
        // WorkManager schedule.
        appScope.launch(Dispatchers.IO) {
            cleanupBackupResources.reconcile()
        }
    }
}
