package de.davis.keygo.feature.backup.domain.model

data class BackupEntry(
    val uri: BackupDestinationUri,
    val name: String,
)
