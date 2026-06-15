package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.export.model.BackupDestination
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportWizardContent(
    state: ExportWizardUiState,
    onEvent: (ExportWizardUiEvent) -> Unit
) {
    val steps = state.steps
    val currentPage = steps.indexOf(state.step).coerceAtLeast(0)
    val pagerState = rememberPagerState(currentPage) { steps.size }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = state.step.title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(ExportWizardUiEvent.Back) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pagerState.pageCount) { page ->
                    val color by animateColorAsState(
                        targetValue = if (page <= pagerState.currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        label = "indicator_page=$page",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .weight(1f)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (steps[page]) {
                        ExportWizardStep.SelectFormat -> SelectFileFormatContent(onEvent = onEvent)
                        ExportWizardStep.Schedule -> SelectScheduleContent(
                            state = state.scheduleState,
                            onEvent = onEvent,
                        )

                        ExportWizardStep.SelectDestination -> SelectDestinationContent(
                            state = state.destinationState,
                            scheduleState = state.scheduleState,
                            format = state.formatState.format,
                            onEvent = onEvent,
                        )

                        ExportWizardStep.ProvidePassphrase -> ProvidePassphraseContent(
                            state = state.providePassphraseState,
                            onEvent = onEvent
                        )

                        ExportWizardStep.Review -> state.formatState.format?.let { format ->
                            ReviewBackupContent(
                                format = format,
                                scheduleState = state.scheduleState,
                                destinationState = state.destinationState,
                                passphraseState = state.providePassphraseState,
                                onEvent = onEvent,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ExportWizardStep.title
    @Composable
    get() = when (this) {
        ExportWizardStep.SelectFormat -> stringResource(R.string.select_file_format_title)
        ExportWizardStep.Schedule -> stringResource(R.string.select_schedule_title)
        ExportWizardStep.SelectDestination -> stringResource(R.string.select_destination_title)
        ExportWizardStep.ProvidePassphrase -> stringResource(R.string.provide_passphrase_title)
        ExportWizardStep.Review -> stringResource(R.string.review_backup_title)
    }

private class ExportWizardUiStateProvider : PreviewParameterProvider<ExportWizardUiState> {

    override val values = ExportWizardStep.entries.asSequence().map {
        ExportWizardUiState(
            formatState = SelectFormatState(format = FileFormat.KDBX),
            scheduleState = SelectScheduleState(
                mode = ScheduleMode.Recurring,
                interval = BackupInterval.Day(3),
            ),
            destinationState = SelectDestinationState(
                destination = BackupDestination(
                    providerLabel = "Nextcloud",
                    displayPath = "Backups / KeyGo",
                ),
            ),
            providePassphraseState = ProvidePassphraseState(
                passphraseTextFieldState = TextFieldState(),
                confirmPassphraseTextFieldState = TextFieldState(),
                passphraseScore = PasswordScore.Strong,
            ),
            step = it,
        )
    }
}

@Preview
@Composable
private fun BackupHubContentPreview(@PreviewParameter(ExportWizardUiStateProvider::class) state: ExportWizardUiState) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ExportWizardContent(
                state = state,
                onEvent = {}
            )
        }
    }
}