package de.davis.keygo.feature.backup.domain.model

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData

data class BackupJob(
    val uri: BackupDestinationUri,
    val passphrase: CryptographicData?,
    val format: FileFormat,
)
