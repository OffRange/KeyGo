package de.davis.keygo.legacy_migration.domain.model

/** Why one v1 row could not be imported. The row stays in the legacy database either way. */
enum class LegacyFailureReason {
    /**
     * The row's blob never became a detail: it did not decrypt under v1's key, or the JSON inside
     * it could not be read. A file whose key is gone does not arrive here at all, that is a
     * whole-run KeyUnavailable, which stops the run instead of failing rows one at a time.
     */
    Unreadable,

    /** The nested password blob did not decrypt, so the login would have been silently emptied. */
    UndecryptablePassword,
}
