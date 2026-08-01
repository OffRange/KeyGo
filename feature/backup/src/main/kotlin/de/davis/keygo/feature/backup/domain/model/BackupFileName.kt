package de.davis.keygo.feature.backup.domain.model

const val BACKUP_BASE_NAME = "keygo-backup"

/**
 * e.g. `keygo-backup-1700000000000.json`. The embedded epoch-millis timestamp keeps names unique per
 * run and lexicographically ordered by recency, which the pruning logic relies on.
 */
fun FileFormat.backupFileName(timestamp: Long): String =
    "$BACKUP_BASE_NAME-$timestamp.$extension"
