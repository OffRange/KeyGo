package de.davis.keygo.feature.backup.domain.model

data class ExportDetails(
    val format: FileFormat,
    val interval: BackupInterval?,
    val passphrase: String,
    val uri: BackupDestinationUri,
)
