package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.IntervalUnit

@Stable
internal data class SelectScheduleState(
    val mode: ScheduleMode = ScheduleMode.Recurring,
    val interval: BackupInterval = BackupInterval(count = 3, unit = IntervalUnit.Days),
    val keepCount: Int = 5,
    val keepAll: Boolean = false,
)