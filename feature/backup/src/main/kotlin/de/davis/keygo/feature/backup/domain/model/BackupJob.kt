package de.davis.keygo.feature.backup.domain.model

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData

data class BackupJob(
    val uri: BackupDestinationUri,
    val wrappedPassphrase: CryptographicData?,
    val format: FileFormat,
    // How JSON payloads are sealed; null for CSV. Persisted jobs without the field are Passphrase.
    val encryption: EncryptionMethod? = null,
    // CSV column layout; null for JSON. Persisted jobs without the field are Browser.
    val csvPreset: CsvPreset? = null,
    // Number of backups to retain in the destination folder; null means keep all (never prune).
    val keepCount: Int? = null,
    val createdAt: Long = 0L,
    val finishedAt: Long? = null,
    val lastResult: BackupResult? = null,
    val cancelled: Boolean = false,
)
