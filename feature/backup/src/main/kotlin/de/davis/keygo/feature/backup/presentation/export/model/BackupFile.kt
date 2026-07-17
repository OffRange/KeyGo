package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.BACKUP_BASE_NAME
import de.davis.keygo.feature.backup.domain.model.FileFormat

/** An illustrative document name shown on the destination card (actual files are timestamped). */
internal fun FileFormat?.backupFileName(): String = when (this) {
    FileFormat.JSON -> "$BACKUP_BASE_NAME.json"
    FileFormat.CSV -> "$BACKUP_BASE_NAME.csv"
    null -> BACKUP_BASE_NAME
}
