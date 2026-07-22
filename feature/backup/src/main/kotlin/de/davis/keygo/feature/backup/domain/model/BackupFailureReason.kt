package de.davis.keygo.feature.backup.domain.model

/**
 * Why a backup failed, in a form that survives persistence and can be shown to the user.
 *
 * Deliberately narrower than [ExportError]: retryable errors never reach a terminal record. It does
 * not carry the Rust cause's free-form message (that would leak cryptic internals into the record
 * and the UI); instead a serialization failure is split into the format-specific sub-cases that the
 * export path can actually produce, so the hub row can name what went wrong.
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
