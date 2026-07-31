package de.davis.keygo.feature.backup.domain.alias

/** The key a [de.davis.keygo.feature.backup.domain.repository.BackupJobRepository] record and the
 * scheduler's outstanding work are both keyed by - a WorkManager request id for one-time jobs, or
 * [de.davis.keygo.feature.backup.worker.BackupWorker.RECURRING_WORK_ID] for the recurring one. */
typealias WorkId = String
