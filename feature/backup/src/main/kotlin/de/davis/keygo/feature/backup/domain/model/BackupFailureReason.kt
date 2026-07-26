package de.davis.keygo.feature.backup.domain.model

/**
 * Why a backup failed, in a form that survives persistence and can be shown to the user.
 *
 * Deliberately narrower than [ExportError]: retryable errors never reach a terminal record, and a
 * serialization failure is split into the sub-cases the export path can actually produce so the hub
 * row can name what went wrong.
 *
 * Names are persisted verbatim in `backup_jobs.pb` - renaming a constant orphans existing records.
 */
enum class BackupFailureReason {
    NothingToExport,
    CryptoFailed,
    SerializationFailed,
    CryptoSerializationFailed,

    WriteFailed,
    NotProvisioned,
}
