package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.FileFormat

internal const val BACKUP_BASE_NAME = "keygo-backup"

internal fun FileFormat?.backupFileName(): String = when (this) {
    FileFormat.JSON -> "$BACKUP_BASE_NAME.json"
    FileFormat.CSV -> "$BACKUP_BASE_NAME.csv"
    null -> BACKUP_BASE_NAME
}
