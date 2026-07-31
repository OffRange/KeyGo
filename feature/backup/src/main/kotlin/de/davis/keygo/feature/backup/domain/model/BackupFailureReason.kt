package de.davis.keygo.feature.backup.domain.model

/**
 * Why a backup failed, in a form that survives persistence and can be shown to the user.
 *
 * Deliberately narrower than [ExportError]: a retryable error only reaches a terminal record once
 * its retries run out (as [RetriesExhausted]), and a serialization failure is split into the
 * sub-cases the export path can actually produce so the hub row can name what went wrong.
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

    /** The device stayed locked (or the session stayed closed) for every attempt the job had. */
    RetriesExhausted,
}
