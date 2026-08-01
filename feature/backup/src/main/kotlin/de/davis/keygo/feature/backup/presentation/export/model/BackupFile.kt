package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.BACKUP_BASE_NAME
import de.davis.keygo.feature.backup.domain.model.FileFormat

/** An illustrative document name shown on the destination card (actual files are timestamped). */
internal fun FileFormat?.backupFileName(): String =
    if (this == null) BACKUP_BASE_NAME else "$BACKUP_BASE_NAME.$extension"
