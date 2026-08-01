package de.davis.keygo.feature.backup.domain.model

data class ExportDetails(
    val format: FileFormat,
    val interval: BackupInterval?,
    val passphrase: String,
    val uri: BackupDestinationUri,
    // null for one-time exports and for "keep all" recurring; otherwise the retention limit.
    val keepCount: Int? = null,
    val encryption: EncryptionMethod? = null,
    val csvPreset: CsvPreset? = null,
)
