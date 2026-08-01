package de.davis.keygo.feature.backup.data.repository

import de.davis.keygo.feature.backup.worker.BackupWorker

// WorkManager eventually drops its own finished work entries, but this DataStore does not, and every
// one-time backup persists a permanent entry keyed by its work id. Bound that growth: the recurring
// record and any job that has not finished yet are retained unconditionally; among finished one-time
// jobs only the most recent [max] survive, ordered by when they finished.
internal const val MAX_FINISHED_ONE_TIME_JOBS = 10

internal fun retainedJobKeys(
    finishedAtByKey: Map<String, Long?>,
    max: Int = MAX_FINISHED_ONE_TIME_JOBS,
    recurringKey: String = BackupWorker.RECURRING_WORK_ID,
): Set<String> {
    val (cappable, alwaysRetained) = finishedAtByKey.entries.partition { (key, finishedAt) ->
        key != recurringKey && finishedAt != null
    }
    val survivors = cappable
        .sortedByDescending { it.value ?: 0L }
        .take(max)
        .map { it.key }
    return alwaysRetained.mapTo(mutableSetOf()) { it.key }.apply { addAll(survivors) }
}
