package de.davis.keygo.feature.backup.presentation.hub

import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupGroup

private val GROUP_ORDER = listOf(
    DispatchedBackup.State.Running,
    DispatchedBackup.State.Failed,
    DispatchedBackup.State.Enqueued,
    DispatchedBackup.State.Cancelled,
    DispatchedBackup.State.Succeeded,
)

internal fun List<DispatchedBackup>.toGroups(): List<BackupGroup> {
    val byState = groupBy { it.state }
    return GROUP_ORDER.mapNotNull { state ->
        val items = byState[state]?.sortedByDescending { it.timestamp } ?: return@mapNotNull null
        BackupGroup(state, items)
    }
}
