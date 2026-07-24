package de.davis.keygo.feature.backup.domain.model

import de.davisalessandro.keygo.rust.ColumnMapping

data class ImportRequest(
    val uri: BackupDestinationUri,
    val format: FileFormat,
    val passphrase: String?,
    val csvMapping: ColumnMapping? = null,
    /** `null` restores the vaults named in the backup; see [ImportTarget]. */
    val target: ImportTarget? = null,
)
