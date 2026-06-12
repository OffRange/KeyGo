package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardStep
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiState
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.SelectFormatState
import de.davis.keygo.feature.backup.presentation.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportWizardContent(
    state: ExportWizardUiState,
    onEvent: (ExportWizardUiEvent) -> Unit
) {
    val pagerState = rememberPagerState(state.step.ordinal) { ExportWizardStep.entries.size }
    LaunchedEffect(state.step) {
        if (pagerState.currentPage != state.step.ordinal) {
            pagerState.animateScrollToPage(state.step.ordinal)
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
                .padding(horizontal = 4.dp),
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
                    when (ExportWizardStep.entries[page]) {
                        ExportWizardStep.SelectFormat -> SelectFileFormatContent(onEvent = onEvent)
                        ExportWizardStep.ProvidePassphrase -> ProvidePassphraseContent(
                            state = state.providePassphraseState,
                            onEvent = onEvent
                        )
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
        ExportWizardStep.ProvidePassphrase -> stringResource(R.string.provide_passphrase_title)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectFileFormatContent(onEvent: (ExportWizardUiEvent) -> Unit) {
    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            FileFormat.entries.forEachIndexed { index, type ->
                SegmentedListItem(
                    onClick = { onEvent(ExportWizardUiEvent.FileFormatSelected(type)) },
                    shapes = ListItemDefaults.segmentedShapes(index, FileFormat.entries.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = if (type.recommented) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    supportingContent = {
                        Text(text = type.description)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                        )
                    },
                    overlineContent = when {
                        type.recommented -> {
                            { Text(text = stringResource(R.string.recommented)) }
                        }

                        else -> null
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = type.displayName)
                }
            }
        }
    }
}


private val FileFormat.description
    @Composable
    get() = when (this) {
        FileFormat.KDBX -> stringResource(R.string.export_description_kdbx)
        FileFormat.CSV -> stringResource(R.string.export_description_csv)
    }


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProvidePassphraseContent(
    state: ProvidePassphraseState,
    onEvent: (ExportWizardUiEvent) -> Unit
) {
    var passphraseHidden by rememberSaveable { mutableStateOf(true) }
    var confirmPassphraseHidden by rememberSaveable { mutableStateOf(true) }
    var forceCompact by rememberSaveable { mutableStateOf(false) }
    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.export_passphrase_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedSecureTextField(
                state = state.passphraseTextFieldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        forceCompact = !it.hasFocus
                    },
                label = {
                    Text(text = stringResource(R.string.passphrase))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Password,
                        contentDescription = null,
                    )
                },
                textObfuscationMode = if (passphraseHidden) TextObfuscationMode.RevealLastTyped
                else TextObfuscationMode.Visible,
                trailingIcon = {
                    VisibilityButton(
                        isHidden = passphraseHidden,
                        onClick = { passphraseHidden = !passphraseHidden },
                    )
                },
            )
            StrengthIndicator(
                passwordScore = state.passphraseScore,
                forceCompact = forceCompact,
            )

            OutlinedSecureTextField(
                state = state.confirmPassphraseTextFieldState,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(R.string.confirm_passphrase))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Password,
                        contentDescription = null,
                    )
                },
                textObfuscationMode = if (confirmPassphraseHidden) TextObfuscationMode.RevealLastTyped
                else TextObfuscationMode.Visible,
                trailingIcon = {
                    VisibilityButton(
                        isHidden = confirmPassphraseHidden,
                        onClick = { confirmPassphraseHidden = !confirmPassphraseHidden },
                    )
                },
            )

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { onEvent(ExportWizardUiEvent.Continue) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.continue_step))
            }
        }
    }
}

private class ExportWizardUiStateProvider : PreviewParameterProvider<ExportWizardUiState> {

    override val values = ExportWizardStep.entries.asSequence().map {
        ExportWizardUiState(
            formatState = SelectFormatState(),
            providePassphraseState = ProvidePassphraseState(
                passphraseTextFieldState = TextFieldState(),
                confirmPassphraseTextFieldState = TextFieldState(),
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