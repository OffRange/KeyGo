package de.davis.keygo.feature.backup.domain.model

import de.davisalessandro.keygo.rust.BackupException

sealed interface ExportError {
    data object SessionLocked : ExportError
    data object NothingToExport : ExportError
    data object CryptoFailed : ExportError
    data class SerializationFailed(val cause: BackupException) : ExportError
    data object WriteFailed : ExportError
    data object NotProvisioned : ExportError
    data object DeviceLocked : ExportError
}

/**
 * Failures that mean "try again later", not "this backup failed". A retryable outcome must never be
 * recorded as terminal and must never release the job's credentials - the retry still needs them.
 */
internal val ExportError.retryable: Boolean
    get() = this == ExportError.DeviceLocked || this == ExportError.SessionLocked

/** Null when the error is [retryable], and so never becomes a terminal failure. */
internal val ExportError.failureReason: BackupFailureReason?
    get() = when (this) {
        ExportError.SessionLocked, ExportError.DeviceLocked -> null
        ExportError.NothingToExport -> BackupFailureReason.NothingToExport
        ExportError.CryptoFailed -> BackupFailureReason.CryptoFailed
        is ExportError.SerializationFailed -> cause.failureReason
        ExportError.WriteFailed -> BackupFailureReason.WriteFailed
        ExportError.NotProvisioned -> BackupFailureReason.NotProvisioned
    }

// The Rust cause names what went wrong; keep only the variants the export path can raise and fold
// the import-only ones into the generic reason. The free-form message is intentionally dropped.
private val BackupException.failureReason: BackupFailureReason
    get() = when (this) {
        is BackupException.Crypto -> BackupFailureReason.CryptoSerializationFailed
        else -> BackupFailureReason.SerializationFailed
    }
