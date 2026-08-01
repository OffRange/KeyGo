package de.davis.keygo.feature.backup.presentation.hub

import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupGroup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupSection

// A recurring backup that is merely enqueued is waiting for its next period, not working.
internal val DispatchedBackup.section: BackupSection
    get() = when (state) {
        is DispatchedBackup.State.Running -> BackupSection.InProgress

        DispatchedBackup.State.Enqueued -> if (kind == DispatchedBackup.Kind.Recurring) BackupSection.Scheduled
        else BackupSection.InProgress

        DispatchedBackup.State.Succeeded,
        DispatchedBackup.State.Failed,
        DispatchedBackup.State.Cancelled -> BackupSection.Recent
    }

internal fun List<DispatchedBackup>.toGroups(): List<BackupGroup> {
    val bySection = groupBy { it.section }
    return BackupSection.entries.mapNotNull { section ->
        val items = bySection[section]?.sortedByDescending { it.timestamp }
            ?: return@mapNotNull null
        BackupGroup(section, items)
    }
}
