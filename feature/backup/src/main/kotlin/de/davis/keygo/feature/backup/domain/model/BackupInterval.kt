package de.davis.keygo.feature.backup.domain.model

sealed interface BackupInterval {
    data class Day(val count: Int) : BackupInterval
    data class Week(val count: Int) : BackupInterval
}