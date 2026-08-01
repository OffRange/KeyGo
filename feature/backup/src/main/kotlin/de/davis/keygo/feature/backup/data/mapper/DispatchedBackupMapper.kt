package de.davis.keygo.feature.backup.data.mapper

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.worker.BackupWorker

internal const val PROGRESS_KEY_PHASE = "phase"
internal const val PROGRESS_KEY_PROCESSED = "processed"
internal const val PROGRESS_KEY_TOTAL = "total"

internal const val PROGRESS_PHASE_RUNNING = "running"
internal const val PROGRESS_PHASE_WRITING = "writing"

internal fun WorkInfo.toStatus() = BackupWorkStatus(
    id = id.toString(),
    kind = toKind(tags),
    state = toState(
        state = state,
        progress = toProgress(
            phase = progress.getString(PROGRESS_KEY_PHASE),
            processed = progress.getInt(PROGRESS_KEY_PROCESSED, 0),
            total = progress.getInt(PROGRESS_KEY_TOTAL, 0),
        ),
    ),
)

internal fun toState(
    state: WorkInfo.State,
    progress: ExportProgress.InFlight? = null,
): DispatchedBackup.State = when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DispatchedBackup.State.Enqueued
    WorkInfo.State.RUNNING -> DispatchedBackup.State.Running(progress)
    WorkInfo.State.SUCCEEDED -> DispatchedBackup.State.Succeeded
    WorkInfo.State.FAILED -> DispatchedBackup.State.Failed
    WorkInfo.State.CANCELLED -> DispatchedBackup.State.Cancelled
}

internal fun toKind(tags: Set<String>): DispatchedBackup.Kind =
    if (BackupWorker.TAG_RECURRING in tags) DispatchedBackup.Kind.Recurring
    else DispatchedBackup.Kind.OneTime

internal fun toProgress(phase: String?, processed: Int, total: Int): ExportProgress.InFlight? =
    when (phase) {
        PROGRESS_PHASE_WRITING -> ExportProgress.Writing
        PROGRESS_PHASE_RUNNING ->
            if (total > 0) ExportProgress.Running(processed, total) else null

        else -> null
    }

internal fun ExportProgress.InFlight.toProgressData(): Data = when (this) {
    is ExportProgress.Running -> workDataOf(
        PROGRESS_KEY_PHASE to PROGRESS_PHASE_RUNNING,
        PROGRESS_KEY_PROCESSED to processed,
        PROGRESS_KEY_TOTAL to total,
    )

    ExportProgress.Writing -> workDataOf(PROGRESS_KEY_PHASE to PROGRESS_PHASE_WRITING)
}
