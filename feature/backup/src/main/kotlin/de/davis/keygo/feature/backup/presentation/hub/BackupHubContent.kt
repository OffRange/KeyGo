package de.davis.keygo.feature.backup.presentation.hub

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.LastBackup
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.hub.model.BackupGroup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun BackupHubContent(state: BackupHubUiState, onEvent: (BackupHubUiEvent) -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ListItem(
                onClick = {},
                // Workaround to disable clicking but remaining original color schema
                colors = ListItemDefaults.colors(
                    disabledContainerColor = ListItemDefaults.colors().containerColor,
                    disabledContentColor = ListItemDefaults.colors().contentColor,
                    disabledOverlineContentColor = ListItemDefaults.colors().overlineContentColor,
                    disabledLeadingContentColor = ListItemDefaults.colors().leadingContentColor,
                    disabledSupportingContentColor = ListItemDefaults.colors().supportingContentColor,
                ),
                enabled = false,
                overlineContent = { Text(text = stringResource(R.string.last_backup)) },
                leadingContent = {
                    Box(modifier = Modifier.wrapContentHeight()) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null)
                    }
                },
                supportingContent = state.lastBackup?.let { { Text(text = it.destination.displayText()) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = state.lastBackup?.let { relativeTime(it.finishedAt) }
                    ?: stringResource(R.string.never_backed_up))
            }

            FilledTonalButton(
                onClick = { onEvent(BackupHubUiEvent.OnRestoreBackup) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsBackupRestore,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.restore_backup))
            }

            FilledTonalButton(
                onClick = { onEvent(BackupHubUiEvent.OnScheduleBackupClick) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.schedule_new_backup))
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.dispatched_backups),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(modifier = Modifier.weight(1f)) {
                AnimatedContent(state.hasItems) { hasItems ->
                    when {
                        hasItems -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            state.groups.forEach { group ->
                                stickyHeader(key = "header-${group.state}") {
                                    BackupGroupHeader(group)
                                }
                                itemsIndexed(
                                    items = group.items,
                                    key = { _, item -> item.id },
                                ) { idx, item ->
                                    DispatchedBackupRow(
                                        item = item,
                                        index = idx,
                                        count = group.items.size,
                                        onCancel = {
                                            onEvent(BackupHubUiEvent.OnCancelBackup(item.id, item.kind))
                                        },
                                    )
                                }
                            }
                        }

                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.no_dispatched_backups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupGroupHeader(group: BackupGroup) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(group.state.label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DispatchedBackupRow(
    item: DispatchedBackup,
    index: Int,
    count: Int,
    onCancel: () -> Unit,
) {
    val active = item.state == DispatchedBackup.State.Enqueued ||
        item.state == DispatchedBackup.State.Running

    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        overlineContent = { Text(text = item.destination.displayText()) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.format?.let { Text(text = it.displayName) }
                if (item.state == DispatchedBackup.State.Running)
                    BackupProgress(progress = item.progress)
            }
        },
        trailingContent = if (active) {
            {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel_backup),
                    )
                }
            }
        } else null,
    ) {
        Text(text = stringResource(item.kind.label))
    }
}

@Composable
private fun BackupProgress(progress: ExportProgress.InFlight?) {
    when (progress) {
        is ExportProgress.Running -> {
            LinearProgressIndicator(
                progress = { progress.processed.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.backup_progress, progress.processed, progress.total),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        ExportProgress.Writing, null ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

@Preview
@Composable
private fun BackupHubContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BackupHubContent(
                state = BackupHubUiState(
                    lastBackup = LastBackup(
                        finishedAt = System.currentTimeMillis(),
                        destination = BackupDestination(
                            provider = BackupDestination.Provider.ThirdParty("Drive"),
                            displayPath = "Drive/Backups",
                        ),
                    ),
                    groups = listOf(
                        DispatchedBackup(
                            id = "1",
                            kind = DispatchedBackup.Kind.Recurring,
                            state = DispatchedBackup.State.Running,
                            format = FileFormat.JSON,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.ThirdParty("Drive"),
                                displayPath = "Drive/Backups",
                            ),
                            progress = ExportProgress.Running(2, 5),
                            timestamp = 2L,
                        ),
                        DispatchedBackup(
                            id = "2",
                            kind = DispatchedBackup.Kind.OneTime,
                            state = DispatchedBackup.State.Succeeded,
                            format = FileFormat.CSV,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.OnDevice,
                                displayPath = "Internal storage/Backups",
                            ),
                            progress = null,
                            timestamp = 1L,
                        ),
                    ).toGroups(),
                ),
                onEvent = {},
            )
        }
    }
}
