package de.davis.keygo.feature.backup.domain.model

data class LastBackup(
    val finishedAt: Long,
    val destination: BackupDestination?,
)
