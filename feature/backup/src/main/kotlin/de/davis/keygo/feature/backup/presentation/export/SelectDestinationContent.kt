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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.component.BackupFileChooser
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackupFileChooser(
            destination = state.destination,
            onChoose = { onEvent(ExportWizardUiEvent.ChooseDestination) },
            chooserIcon = Icons.Default.CreateNewFolder,
            chooserTitle = stringResource(R.string.destination_choose_title),
            chooserSubtitle = stringResource(R.string.destination_choose_subtitle),
            chooserAction = stringResource(R.string.destination_choose_action),
            changeLabel = stringResource(R.string.file_chooser_change),
            fileNameLabel = stringResource(
                R.string.destination_filename_label,
                state.destination?.fileName ?: format.backupFileName()
            ),
        )

        BehaviorHint(mode = scheduleState.mode, keepAll = scheduleState.keepAll)
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
                format = FileFormat.JSON,
                onEvent = {},
            )
        }
    }
}
