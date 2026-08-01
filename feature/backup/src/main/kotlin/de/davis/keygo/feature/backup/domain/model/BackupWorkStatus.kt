package de.davis.keygo.feature.backup.domain.model

data class BackupWorkStatus(
    val id: String,
    val kind: DispatchedBackup.Kind,
    val state: DispatchedBackup.State,
)
