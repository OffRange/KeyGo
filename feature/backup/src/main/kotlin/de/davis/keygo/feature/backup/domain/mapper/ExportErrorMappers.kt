package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davisalessandro.keygo.rust.BackupException

/**
 * A locked session is the one Rust failure the export path can recover from, so it must not be
 * folded in with the serialization errors.
 *
 * [ExportError.SerializationFailed] is terminal: it carries a [BackupFailureReason], which records
 * the job as failed and releases the escrowed credentials the retry would have needed. The session
 * can lock at any point after [de.davis.keygo.feature.backup.domain.BackupArkUnlocker] hands back
 * the live session, because auto-lock fires from the lock observer rather than from this flow.
 * Mapping that to [ExportError.SessionLocked] keeps the job retryable and its escrow intact.
 */
internal fun BackupException.toExportError(): ExportError = when (this) {
    is BackupException.Locked -> ExportError.SessionLocked
    else -> ExportError.SerializationFailed(this)
}
