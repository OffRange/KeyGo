package de.davis.keygo.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import de.davis.keygo.feature.backup.domain.model.BackupInterval

internal val BackupInterval.displayName
    @Composable
    get() = when (this) {
        is BackupInterval.Day -> pluralStringResource(
            R.plurals.backup_interval_days,
            count = count,
            count
        )

        is BackupInterval.Week -> pluralStringResource(
            R.plurals.backup_interval_weeks,
            count = count,
            count
        )
    }