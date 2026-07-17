package de.davis.keygo.feature.backup.domain.model

/** An existing backup document discovered inside a destination folder. */
data class BackupEntry(
    val uri: BackupDestinationUri,
    val name: String,
)
