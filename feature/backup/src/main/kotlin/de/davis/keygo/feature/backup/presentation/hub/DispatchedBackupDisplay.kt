package de.davis.keygo.feature.backup.presentation.hub

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup

@get:StringRes
internal val DispatchedBackup.State.label: Int
    get() = when (this) {
        DispatchedBackup.State.Enqueued -> R.string.backup_state_enqueued
        DispatchedBackup.State.Running -> R.string.backup_state_running
        DispatchedBackup.State.Succeeded -> R.string.backup_state_succeeded
        DispatchedBackup.State.Failed -> R.string.backup_state_failed
        DispatchedBackup.State.Cancelled -> R.string.backup_state_cancelled
    }

@get:StringRes
internal val DispatchedBackup.Kind.label: Int
    get() = when (this) {
        DispatchedBackup.Kind.OneTime -> R.string.backup_kind_one_time
        DispatchedBackup.Kind.Recurring -> R.string.backup_kind_recurring
    }

@Composable
internal fun BackupDestination?.displayText(): String {
    val destination = this ?: return stringResource(R.string.destination_provider_unknown)
    return when (val provider = destination.provider) {
        BackupDestination.Provider.Unknown ->
            stringResource(R.string.destination_provider_unknown)

        BackupDestination.Provider.OnDevice ->
            destination.displayPath.ifBlank { stringResource(R.string.destination_provider_on_device) }

        is BackupDestination.Provider.ThirdParty ->
            destination.displayPath.ifBlank { provider.name }
    }
}
