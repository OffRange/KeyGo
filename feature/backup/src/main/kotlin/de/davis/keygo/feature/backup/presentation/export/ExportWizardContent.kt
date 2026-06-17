package de.davis.keygo.feature.backup.presentation.export

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportWizardContent(
    state: ExportWizardUiState,
    onEvent: (ExportWizardUiEvent) -> Unit,
    navigateUp: () -> Unit,
) {
    val steps = state.steps
    val currentStep = state.step
    val currentIndex = steps.indexOf(currentStep).coerceAtLeast(0)

    val transitionState = remember { SeekableTransitionState(currentStep) }
    val transition = rememberTransition(transitionState, label = "export_wizard_step")
    val resetScope = rememberCoroutineScope()

    LaunchedEffect(currentStep) {
        if (transitionState.currentState != currentStep)
            transitionState.animateTo(currentStep)
    }

    PredictiveBackHandler(enabled = currentIndex > 0) { progress ->
        val previousStep = steps[currentIndex - 1]
        try {
            progress.collect { backEvent ->
                transitionState.seekTo(backEvent.progress, targetState = previousStep)
            }
            onEvent(ExportWizardUiEvent.Back)
        } catch (_: CancellationException) {
            resetScope.launch { transitionState.animateTo(transitionState.currentState) }
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
                        onClick = {
                            if (currentIndex == 0) navigateUp()
                            else onEvent(ExportWizardUiEvent.Back)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 4.dp)
                    .imePadding(),
            ) {
                Button(
                    onClick = { onEvent(ExportWizardUiEvent.Continue) },
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val recurring = state.scheduleState.mode == ScheduleMode.Recurring
                    val isReview = state.step == ExportWizardStep.Review
                    AnimatedVisibility(
                        visible = isReview
                    ) {
                        Icon(
                            imageVector = if (recurring) Icons.Default.Schedule else Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = ButtonDefaults.IconSpacing)
                                .size(ButtonDefaults.IconSize),
                        )
                    }
                    Text(
                        text = stringResource(
                            when {
                                isReview && recurring -> R.string.schedule_backup
                                isReview -> R.string.create_backup
                                else -> R.string.continue_step
                            }
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(start = 4.dp, end = 4.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(steps.size) { page ->
                    val color by animateColorAsState(
                        targetValue = if (page <= currentIndex) MaterialTheme.colorScheme.primary
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
            transition.AnimatedContent(
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(animationSpec = tween(700)) togetherWith
                            fadeOut(animationSpec = tween(700))
                },
            ) { step ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (step) {
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
                        )

                        ExportWizardStep.Review -> state.formatState.format?.let { format ->
                            ReviewBackupContent(
                                format = format,
                                scheduleState = state.scheduleState,
                                destinationState = state.destinationState,
                                passphraseState = state.providePassphraseState,
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
            scheduleState = SelectScheduleState(),
            destinationState = SelectDestinationState(
                destination = BackupDestination(
                    provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
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
                onEvent = {},
                navigateUp = {},
            )
        }
    }
}