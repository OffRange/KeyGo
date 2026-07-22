package de.davis.keygo.feature.backup.presentation.hub.model

import androidx.annotation.StringRes
import de.davis.keygo.feature.backup.R

internal enum class BackupSection(@get:StringRes val label: Int) {
    InProgress(R.string.backup_section_in_progress),
    Scheduled(R.string.backup_section_scheduled),
    Recent(R.string.backup_section_recent),
}
