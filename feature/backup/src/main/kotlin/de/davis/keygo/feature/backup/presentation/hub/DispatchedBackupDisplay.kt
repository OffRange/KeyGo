package de.davis.keygo.feature.backup.presentation.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup

internal val DispatchedBackup.icon: ImageVector
    get() = when (state) {
        is DispatchedBackup.State.Running -> Icons.Default.CloudUpload
        DispatchedBackup.State.Enqueued -> Icons.Default.Schedule
        DispatchedBackup.State.Succeeded -> Icons.Default.Check
        DispatchedBackup.State.Failed -> Icons.Default.Close
        DispatchedBackup.State.Cancelled -> Icons.Default.Block
    }

internal data class StatusColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
internal fun DispatchedBackup.statusColors(): StatusColors = with(MaterialTheme.colorScheme) {
    when (state) {
        is DispatchedBackup.State.Running -> StatusColors(primaryContainer, onPrimaryContainer)
        DispatchedBackup.State.Enqueued -> StatusColors(secondaryContainer, onSecondaryContainer)
        DispatchedBackup.State.Succeeded -> StatusColors(tertiaryContainer, onTertiaryContainer)
        DispatchedBackup.State.Failed -> StatusColors(errorContainer, onErrorContainer)
        DispatchedBackup.State.Cancelled -> StatusColors(surfaceContainerHighest, onSurfaceVariant)
    }
}

internal val DispatchedBackup.Kind.label: String
    @Composable
    get() = when (this) {
        DispatchedBackup.Kind.OneTime -> R.string.backup_kind_one_time
        DispatchedBackup.Kind.Recurring -> R.string.backup_kind_recurring
    }.let { stringResource(it) }

internal val BackupFailureReason.label: String
    @Composable
    get() = when (this) {
        BackupFailureReason.NothingToExport -> R.string.backup_failure_nothing_to_export
        BackupFailureReason.CryptoFailed -> R.string.backup_failure_crypto
        BackupFailureReason.SerializationFailed -> R.string.backup_failure_serialization
        BackupFailureReason.CryptoSerializationFailed -> R.string.backup_failure_serialization_crypto
        BackupFailureReason.WriteFailed -> R.string.backup_failure_write
        BackupFailureReason.NotProvisioned -> R.string.backup_failure_not_provisioned
    }.let { stringResource(it) }

@Composable
internal fun BackupDestination?.displayText(): String {
    val destination = this ?: return stringResource(R.string.destination_provider_unknown)
    return when (val provider = destination.provider) {
        BackupDestination.Provider.Unknown -> stringResource(R.string.destination_provider_unknown)

        BackupDestination.Provider.OnDevice ->
            destination.displayPath.ifBlank { stringResource(R.string.destination_provider_on_device) }

        is BackupDestination.Provider.ThirdParty ->
            destination.displayPath.ifBlank { provider.name }
    }
}
