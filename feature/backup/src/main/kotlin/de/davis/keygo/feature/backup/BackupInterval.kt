package de.davis.keygo.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.presentation.export.model.IntervalUnit

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

internal val BackupInterval.intervalCount: Int
    get() = when (this) {
        is BackupInterval.Day -> count
        is BackupInterval.Week -> count
    }

internal val BackupInterval.intervalUnit: IntervalUnit
    get() = when (this) {
        is BackupInterval.Day -> IntervalUnit.Days
        is BackupInterval.Week -> IntervalUnit.Weeks
    }

internal fun backupInterval(unit: IntervalUnit, count: Int): BackupInterval {
    val safeCount = count.coerceAtLeast(1)
    return when (unit) {
        IntervalUnit.Days -> BackupInterval.Day(safeCount)
        IntervalUnit.Weeks -> BackupInterval.Week(safeCount)
    }
}

internal val IntervalUnit.label
    @Composable
    get() = when (this) {
        IntervalUnit.Days -> stringResource(R.string.interval_unit_days)
        IntervalUnit.Weeks -> stringResource(R.string.interval_unit_weeks)
    }