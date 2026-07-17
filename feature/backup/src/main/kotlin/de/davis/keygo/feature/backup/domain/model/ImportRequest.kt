package de.davis.keygo.feature.backup.domain.model

data class ImportRequest(
    val uri: BackupDestinationUri,
    val format: FileFormat,
    val passphrase: String?,
)
