package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectScheduleContent(
    state: SelectScheduleState,
    onEvent: (ExportWizardUiEvent) -> Unit,
) {
    Surface {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ScheduleMode.entries.forEachIndexed { index, mode ->
                    val selected = state.mode == mode
                    SegmentedListItem(
                        checked = selected,
                        onCheckedChange = { onEvent(ExportWizardUiEvent.ScheduleModeSelected(mode)) },
                        shapes = ListItemDefaults.segmentedShapes(index, ScheduleMode.entries.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ),
                        leadingContent = {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(mode.descriptionRes))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(mode.titleRes))
                    }

                    AnimatedVisibility(
                        visible = selected && mode == ScheduleMode.Recurring,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            IntervalPicker(
                                interval = state.interval,
                                onEvent = onEvent,
                                shape = SegmentTopShape
                            )
                            RetentionPicker(
                                keepCount = state.keepCount,
                                keepAll = state.keepAll,
                                onEvent = onEvent,
                                shape = SegmentBottomShape
                            )
                        }
                    }
                }
            }

            ContinueButton(onEvent = onEvent)
        }
    }
}

private class SelectScheduleStateProvider : PreviewParameterProvider<SelectScheduleState> {
    override val values = sequenceOf(
        SelectScheduleState(mode = ScheduleMode.OneTime),
        SelectScheduleState(
            mode = ScheduleMode.Recurring,
            interval = BackupInterval.Day(3),
        ),
    )
}

@Preview
@Composable
private fun SelectScheduleContentPreview(
    @PreviewParameter(SelectScheduleStateProvider::class) state: SelectScheduleState,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            SelectScheduleContent(
                state = state,
                onEvent = {},
            )
        }
    }
}
