package de.davis.keygo.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.IntervalUnit

internal val BackupInterval.displayName
    @Composable
    get() = when (unit) {
        IntervalUnit.Days -> pluralStringResource(
            R.plurals.backup_interval_days,
            count = count,
            count
        )

        IntervalUnit.Weeks -> pluralStringResource(
            R.plurals.backup_interval_weeks,
            count = count,
            count
        )
    }

internal val IntervalUnit.label
    @Composable
    get() = when (this) {
        IntervalUnit.Days -> stringResource(R.string.interval_unit_days)
        IntervalUnit.Weeks -> stringResource(R.string.interval_unit_weeks)
    }
