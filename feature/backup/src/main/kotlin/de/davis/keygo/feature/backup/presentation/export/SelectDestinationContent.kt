package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.export.component.IconBadge
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState
import de.davis.keygo.feature.backup.presentation.export.model.backupFileName

@Composable
internal fun SelectDestinationContent(
    state: SelectDestinationState,
    scheduleState: SelectScheduleState,
    format: FileFormat?,
    onEvent: (ExportWizardUiEvent) -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isFile = scheduleState.mode == ScheduleMode.OneTime
            when (val destination = state.destination) {
                null -> DestinationChooserCard(
                    isFile = isFile,
                    onChoose = { onEvent(ExportWizardUiEvent.ChooseDestination) },
                )

                else -> DestinationSelectedCard(
                    destination = destination,
                    fileName = destination.fileName ?: format.backupFileName(),
                    onChange = { onEvent(ExportWizardUiEvent.ChooseDestination) },
                )
            }

            BehaviorHint(mode = scheduleState.mode, keepAll = scheduleState.keepAll)
        }
    }
}

@Composable
private fun DestinationChooserCard(isFile: Boolean, onChoose: () -> Unit) {
    Card(
        onClick = onChoose,
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary,
                shape = CardDefaults.shape,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isFile) Icons.Default.FileUpload else Icons.Default.CreateNewFolder,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.destination_choose_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (isFile) R.string.destination_choose_file_subtitle
                    else R.string.destination_choose_subtitle,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (isFile) R.string.destination_choose_file_action
                    else R.string.destination_choose_action,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: Shape,
    on: Dp = 6.dp,
    off: Dp = 4.dp,
) = drawWithContent {
    drawContent()
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = color,
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())),
        ),
    )
}

@Composable
private fun DestinationSelectedCard(
    destination: BackupDestination,
    fileName: String,
    onChange: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    icon = if (destination.fileName != null) Icons.Default.Description
                    else Icons.Default.Folder,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 44.dp,
                    iconSize = 24.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination.provider.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = destination.displayPath,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                TextButton(onClick = onChange) {
                    Text(text = stringResource(R.string.destination_change))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.destination_filename_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BehaviorHint(mode: ScheduleMode, keepAll: Boolean) {
    val recurring = mode == ScheduleMode.Recurring
    val behaviorRes = when {
        !recurring -> R.string.destination_behavior_one_time
        keepAll -> R.string.destination_behavior_recurring
        else -> R.string.destination_behavior_recurring_pruned
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(behaviorRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BackupDestination.Provider.label
    @Composable
    get() = when (this) {
        BackupDestination.Provider.Unknown -> stringResource(R.string.destination_provider_unknown)
        BackupDestination.Provider.OnDevice -> stringResource(R.string.destination_provider_on_device)
        is BackupDestination.Provider.ThirdParty -> name
    }

private class SelectDestinationStateProvider : PreviewParameterProvider<SelectDestinationState> {
    override val values = sequenceOf(
        SelectDestinationState(),
        SelectDestinationState(
            destination = BackupDestination(
                provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
                displayPath = "Backups / KeyGo",
            ),
        ),
    )
}

@Preview
@Composable
private fun SelectDestinationContentPreview(
    @PreviewParameter(SelectDestinationStateProvider::class) state: SelectDestinationState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            SelectDestinationContent(
                state = state,
                scheduleState = SelectScheduleState(mode = ScheduleMode.Recurring),
                format = FileFormat.KDBX,
                onEvent = {},
            )
        }
    }
}
