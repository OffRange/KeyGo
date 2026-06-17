package de.davis.keygo.feature.backup.domain

import de.davis.keygo.feature.backup.domain.model.BackupInterval

interface BackupScheduler {

    fun scheduleRecurringBackup(interval: BackupInterval)
    fun scheduleOneTimeBackup()
    fun cancel()
}