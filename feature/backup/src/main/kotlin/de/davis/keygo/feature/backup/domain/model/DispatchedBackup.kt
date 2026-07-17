package de.davis.keygo.feature.backup.domain.model

data class DispatchedBackup(
    val id: String,
    val kind: Kind,
    val state: State,
    val format: FileFormat?,
    val destination: BackupDestination?,
    val progress: ExportProgress.InFlight?,
    val timestamp: Long = 0L,
) {
    enum class Kind { OneTime, Recurring }
    enum class State { Enqueued, Running, Succeeded, Failed, Cancelled }
}
