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
