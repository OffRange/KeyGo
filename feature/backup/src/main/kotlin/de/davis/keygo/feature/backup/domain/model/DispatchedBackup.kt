package de.davis.keygo.feature.backup.domain.model

data class DispatchedBackup(
    val id: String,
    val kind: Kind,
    val state: State,
    val format: FileFormat?,
    val destination: BackupDestination?,
    val timestamp: Long = 0L,
    /** Why the run behind this row failed; null unless the job's persisted result is a Failure. */
    val failureReason: BackupFailureReason? = null,
) {
    enum class Kind { OneTime, Recurring }

    sealed interface State {
        data object Enqueued : State

        // In-flight detail lives here rather than in a parallel field, so it cannot outlive the run:
        // a finished backup carries no progress by construction. Null until the worker reports.
        data class Running(val progress: ExportProgress.InFlight? = null) : State

        data object Succeeded : State
        data object Failed : State
        data object Cancelled : State
    }
}
